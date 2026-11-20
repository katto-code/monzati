package com.monzatti.app.ui.feature.tryon

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

private const val MIN_LIKELIHOOD = 0.5f

private val leftJoints = mapOf(
    Joint.SHOULDER to PoseLandmark.LEFT_SHOULDER,
    Joint.ELBOW to PoseLandmark.LEFT_ELBOW,
    Joint.WRIST to PoseLandmark.LEFT_WRIST,
    Joint.HIP to PoseLandmark.LEFT_HIP,
    Joint.KNEE to PoseLandmark.LEFT_KNEE
)

private val rightJoints = mapOf(
    Joint.SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.HIP to PoseLandmark.RIGHT_HIP,
    Joint.KNEE to PoseLandmark.RIGHT_KNEE
)

private fun Pose.side(joints: Map<Joint, Int>): Map<Joint, Pt> = buildMap {
    joints.forEach { (joint, type) ->
        getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= MIN_LIKELIHOOD }
            ?.let { put(joint, Pt(it.position.x, it.position.y)) }
    }
}

fun Pose.toBodyFrame(): BodyFrame? = BodyFrame.from(side(leftJoints), side(rightJoints))

@Composable
fun rememberPoseController(): LifecycleCameraController {
    val context = LocalContext.current
    return remember { LifecycleCameraController(context) }
}

/**
 * Vista de cámara + detección de pose en el dispositivo (ML Kit). Los puntos llegan ya en
 * coordenadas de la vista (COORDINATE_SYSTEM_VIEW_REFERENCED), incluido el espejo de la cámara frontal.
 */
@Composable
fun PoseCameraPreview(
    useFrontCamera: Boolean,
    onPose: (BodyFrame?) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
    controller: LifecycleCameraController = rememberPoseController()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPose by rememberUpdatedState(onPose)
    val latestOnUnavailable by rememberUpdatedState(onCameraUnavailable)

    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
    }
    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val executor = ContextCompat.getMainExecutor(context)
        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        // Análisis para la pose + captura para la foto con IA.
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS or LifecycleCameraController.IMAGE_CAPTURE)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                latestOnPose(result.getValue(detector)?.toBodyFrame())
            }
        )
        controller.initializationFuture.addListener({
            if (controller.hasCamera(selector)) {
                controller.cameraSelector = selector
                controller.bindToLifecycle(lifecycleOwner)
            } else {
                latestOnUnavailable()
            }
        }, executor)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose { detector.close() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        }
    )
}
