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

    /** 현재 후면 카메라 사용 여부 (true = 후면, false = 전면) */
    private var isBackCamera = true

    /** 손-입 접근 감지 점수 (히스테리시스용). hit이면 증가, miss이면 감소 */
    private var detectionScore = 0

    companion object {
        /** 랜드마크가 화면에 실제로 존재한다고 인정하는 최소 신뢰도 */
        private const val MIN_LANDMARK_LIKELIHOOD = 0.5f

        /** 복용 확정에 필요한 점수 (이 값 이상이면 복용 완료 처리) */
        private const val DETECTION_SCORE_THRESHOLD = 10

        /** hit 1회당 증가하는 점수 */
        private const val SCORE_INCREMENT = 2

        /** miss 1회당 감소하는 점수 (증가보다 작게 하여 잠깐의 흔들림을 허용) */
        private const val SCORE_DECREMENT = 1

        /** "인식 중" 문구를 표시하기 시작하는 점수 */
        private const val DETECTING_HINT_SCORE = 2

        /** 손-입 거리 임계값 = 입 너비 × 이 배수 (값이 클수록 너그럽게 인식) */
        private const val MOUTH_WIDTH_MULTIPLIER = 1.6
    }

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
        binding.btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }
    }

    /**
     * 전면/후면 카메라를 전환한다.
     * cameraController가 초기화된 경우에만 동작한다.
     */
    private fun toggleCamera() {
        if (!::cameraController.isInitialized) return
        isBackCamera = !isBackCamera
        cameraController.cameraSelector = if (isBackCamera) {
            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
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

        // 현재 선택된 카메라(기본: 후면)를 사용
        cameraController.cameraSelector = if (isBackCamera) {
            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
        }
        
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

        // 1) 입이 실제로 화면에 충분히 보여야 함 (신뢰도 확인)
        if (leftMouth == null || rightMouth == null) {
            decayScore()
            return
        }
        if (leftMouth.inFrameLikelihood < MIN_LANDMARK_LIKELIHOOD ||
            rightMouth.inFrameLikelihood < MIN_LANDMARK_LIKELIHOOD
        ) {
            decayScore()
            return
        }

        val mouthCenterX = (leftMouth.position.x + rightMouth.position.x) / 2
        val mouthCenterY = (leftMouth.position.y + rightMouth.position.y) / 2

        // 입 너비에 비례한 거리 임계값 (카메라 거리와 무관하게 동작)
        val mouthWidth = sqrt(
            Math.pow((leftMouth.position.x - rightMouth.position.x).toDouble(), 2.0) +
            Math.pow((leftMouth.position.y - rightMouth.position.y).toDouble(), 2.0)
        )
        val threshold = mouthWidth * MOUTH_WIDTH_MULTIPLIER

        // 2) 손가락은 "화면에 실제로 보일 때만" 거리 판정에 사용
        //    (ML Kit은 손이 안 보여도 좌표를 추정해서 내놓으므로 신뢰도로 걸러야 함)
        val isLeftHandClose = leftIndex?.takeIf {
            it.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD
        }?.let {
            val dist = sqrt(
                Math.pow((it.position.x - mouthCenterX).toDouble(), 2.0) +
                Math.pow((it.position.y - mouthCenterY).toDouble(), 2.0)
            )
            dist < threshold
        } ?: false

        val isRightHandClose = rightIndex?.takeIf {
            it.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD
        }?.let {
            val dist = sqrt(
                Math.pow((it.position.x - mouthCenterX).toDouble(), 2.0) +
                Math.pow((it.position.y - mouthCenterY).toDouble(), 2.0)
            )
            dist < threshold
        } ?: false

        // 3) 점수 누적 방식: hit이면 점수 증가, miss이면 천천히 감소
        //    (한 프레임 흔들림으로 즉시 리셋되지 않도록 하여 인식률을 높이고 문구 깜빡임을 방지)
        if (isLeftHandClose || isRightHandClose) {
            detectionScore = (detectionScore + SCORE_INCREMENT)
                .coerceAtMost(DETECTION_SCORE_THRESHOLD)
        } else {
            detectionScore = (detectionScore - SCORE_DECREMENT).coerceAtLeast(0)
        }

        updateHintByScore()

        if (detectionScore >= DETECTION_SCORE_THRESHOLD) {
            onDosageDetected()
        }
    }

    /**
     * 현재 점수에 따라 안내 문구를 갱신한다.
     * 일정 점수 이상이면 "인식 중", 그 미만이면 기본 안내를 표시한다.
     * 점수가 점진적으로 변하므로 문구가 매 프레임 깜빡이지 않는다.
     */
    private fun updateHintByScore() {
        if (viewModel.isDetected.value) return
        val binding = _binding ?: return
        if (detectionScore >= DETECTING_HINT_SCORE) {
            val target = getString(R.string.ai_detection_detecting)
            if (binding.tvStatus.text != target) binding.tvStatus.text = target
        } else {
            val target = getString(R.string.ai_detection_hint)
            if (binding.tvStatus.text != target) binding.tvStatus.text = target
        }
    }

    /**
     * 입/손이 화면에서 사라졌을 때 점수를 감소시킨다.
     * 즉시 0으로 리셋하지 않아 잠깐의 인식 누락에도 진행 상태가 유지된다.
     */
    private fun decayScore() {
        if (detectionScore > 0) {
            detectionScore = (detectionScore - SCORE_DECREMENT).coerceAtLeast(0)
            updateHintByScore()
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