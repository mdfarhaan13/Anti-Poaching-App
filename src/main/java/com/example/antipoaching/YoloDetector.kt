package com.example.antipoaching

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class YoloDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private val dangerClasses = setOf("gun", "hunter", "poacher", "rifle", "x-bow", "jeep", "vehicle")
    private val classNames = listOf(
        "Animal body parts", "Antelope", "Badger", "Bat", "Bear", "Bison", "Boar", "Chimpanzee", "Coyote", "Deer", "Dog", "Donkey", "Duck", "Eagle", "Elephant", "Flamingo", "Fox", "Goat", "Goose", "Gorilla", "Gun", "Hare", "Hedgehog", "Hippopotamus", "Hornbill", "Horse", "Humming Bird", "Hunter", "Hyena", "Jeep", "Kangaroo", "Koala", "Leopard", "Lion", "Lizard", "Mouse", "Okapi", "Orangutan", "Otter", "Owl", "Ox", "Panda", "Parrot", "Pig", "Pigeon", "Poacher", "Porcupine", "Possum", "Raccoon", "Reindeer", "Rifle", "Rinoceros", "Sandpiper", "Sheep", "Snake", "Sparrow", "Squirrel", "Tiger", "Turkey", "Vehicle", "Wolf", "Wombat", "Woodpecker", "X-Bow", "Zebra"
    )

    init {
        try {
            val modelFile = loadModelFile(context, "best.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelFile, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        if (interpreter == null) return emptyList()

        // Standard YOLOv8 TFLite input is 640x640
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tImage = TensorImage(DataType.FLOAT32)
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        // Output shape for YOLOv8 is usually [1, num_classes + 4, 8400]
        // E.g. [1, 84, 8400] for COCO
        val shape = interpreter?.getOutputTensor(0)?.shape() ?: intArrayOf(1, 69, 8400)
        val dim1 = shape[1]
        val dim2 = shape.getOrElse(2) { 8400 }
        
        val isTransposed = dim1 > dim2 // e.g. 8400 > 69
        val numElements = if (isTransposed) dim2 else dim1
        val numBoxes = if (isTransposed) dim1 else dim2

        val outputBuffer = TensorBuffer.createFixedSize(shape, DataType.FLOAT32)
        
        try {
            interpreter?.run(tImage.buffer, outputBuffer.buffer)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

        val outputArray = outputBuffer.floatArray
        val boundingBoxes = mutableListOf<BoundingBox>()
        
        // --- Pass 1: Quick check for Gun/Weapon presence (Threshold 0.70) ---
        var weaponDetected = false
        val weaponIndices = setOf(20, 50, 63) // Gun, Rifle, X-Bow
        val humanIndices = setOf(27, 45) // Hunter, Poacher
        
        for (i in 0 until numBoxes) {
            for (idx in weaponIndices) {
                val conf = if (isTransposed) outputArray[i * numElements + (idx + 4)] else outputArray[(idx + 4) * numBoxes + i]
                if (conf > 0.70f) {
                    weaponDetected = true
                    break
                }
            }
            if (weaponDetected) break
        }

        // --- Pass 2: Extract all boxes with context-aware thresholds ---
        val rawBoxes = mutableListOf<BoundingBox>()
        
        for (i in 0 until numBoxes) {
            var maxClassConf = 0f
            var maxClassIndex = -1

            for (c in 0 until (numElements - 4)) {
                val conf = if (isTransposed) outputArray[i * numElements + (c + 4)] else outputArray[(c + 4) * numBoxes + i]
                if (conf > maxClassConf) {
                    maxClassConf = conf
                    maxClassIndex = c
                }
            }

            // Context-aware threshold logic
            val threshold = when {
                weaponIndices.contains(maxClassIndex) -> 0.70f // Gun threshold requested by user
                humanIndices.contains(maxClassIndex) -> if (weaponDetected) 0.05f else 0.12f // Boost humans if weapon seen
                else -> 0.40f // Default for animals and vehicles
            }
            
            if (maxClassConf > threshold) {
                val cx = if (isTransposed) outputArray[i * numElements + 0] else outputArray[0 * numBoxes + i]
                val cy = if (isTransposed) outputArray[i * numElements + 1] else outputArray[1 * numBoxes + i]
                val w = if (isTransposed) outputArray[i * numElements + 2] else outputArray[2 * numBoxes + i]
                val h = if (isTransposed) outputArray[i * numElements + 3] else outputArray[3 * numBoxes + i]

                val x1 = (cx - w / 2f) * bitmap.width
                val y1 = (cy - h / 2f) * bitmap.height
                val x2 = (cx + w / 2f) * bitmap.width
                val y2 = (cy + h / 2f) * bitmap.height

                val labelName = if (maxClassIndex < classNames.size) classNames[maxClassIndex] else "unknown"
                val isDanger = dangerClasses.contains(labelName.lowercase())
                
                var label = labelName + " " + String.format(java.util.Locale.US, "%.2f", maxClassConf)
                if (weaponDetected && humanIndices.contains(maxClassIndex)) {
                    label = "[!] " + label
                }

                rawBoxes.add(
                    BoundingBox(x1, y1, x2, y2, maxClassConf, maxClassIndex, label, isDanger)
                )
            }
        }

        return applyNMS(rawBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        // Simple Non-Maximum Suppression
        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val selectedBoxes = mutableListOf<BoundingBox>()

        for (box in sortedBoxes) {
            var shouldSelect = true
            for (selectedBox in selectedBoxes) {
                if (calculateIoU(box, selectedBox) > 0.45f) { // IoU threshold
                    shouldSelect = false
                    break
                }
            }
            if (shouldSelect) {
                selectedBoxes.add(box)
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    fun close() {
        interpreter?.close()
    }
}
