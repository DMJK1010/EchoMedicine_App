package com.echomedicine.app.presentation.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentAiDetectionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@AndroidEntryPoint
class AiDetectionFragment : Fragment() {

    private var _binding: FragmentAiDetectionBinding? = null
    private val binding get() = _binding!!

    private val args: AiDetectionFragmentArgs by navArgs()
    private val viewModel: AiDetectionViewModel by viewModels()

    private lateinit var cameraController: LifecycleCameraController

    private val poseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), R.string.ai_detection_camera_permission_denied, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiDetectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        checkCameraPermission()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @ExperimentalGetImage
    private fun startCamera() {
        val context = requireContext()
        cameraController = LifecycleCameraController(context)
        cameraController.bindToLifecycle(viewLifecycleOwner)

        // Try to find the camera by ID "10" which dumpsys reports as the active one
        // If not found, use DEFAULT_BACK_CAMERA (which maps to 10 in emulator)
        cameraController.cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        
        binding.previewView.controller = cameraController

        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        detectHandToMouth(pose)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun detectHandToMouth(pose: com.google.mlkit.vision.pose.Pose) {
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        val leftMouth = pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH)
        val rightMouth = pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH)

        if (leftMouth == null || rightMouth == null) return

        val mouthCenterX = (leftMouth.position.x + rightMouth.position.x) / 2
        val mouthCenterY = (leftMouth.position.y + rightMouth.position.y) / 2

        // Threshold for detection (relative to mouth width)
        val mouthWidth = sqrt(
            Math.pow((leftMouth.position.x - rightMouth.position.x).toDouble(), 2.0) +
            Math.pow((leftMouth.position.y - rightMouth.position.y).toDouble(), 2.0)
        )
        val threshold = mouthWidth * 1.5 // Adjust based on testing

        val isLeftHandClose = leftIndex?.let {
            val dist = sqrt(
                Math.pow((it.position.x - mouthCenterX).toDouble(), 2.0) +
                Math.pow((it.position.y - mouthCenterY).toDouble(), 2.0)
            )
            dist < threshold
        } ?: false

        val isRightHandClose = rightIndex?.let {
            val dist = sqrt(
                Math.pow((it.position.x - mouthCenterX).toDouble(), 2.0) +
                Math.pow((it.position.y - mouthCenterY).toDouble(), 2.0)
            )
            dist < threshold
        } ?: false

        if (isLeftHandClose || isRightHandClose) {
            onDosageDetected()
        }
    }

    private fun onDosageDetected() {
        if (viewModel.isDetected.value) return
        
        binding.tvStatus.text = getString(R.string.ai_detection_success)
        binding.tvStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.slot_status_taken))
        
        viewModel.markAsTaken(args.slotNumber)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isDetected.collect { isDetected ->
                    if (isDetected) {
                        Toast.makeText(requireContext(), R.string.ai_detection_success, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}