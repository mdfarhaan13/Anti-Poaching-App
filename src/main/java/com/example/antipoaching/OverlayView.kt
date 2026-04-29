package com.example.antipoaching

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val boundingBoxes = mutableListOf<BoundingBox>()
    
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }
    
    private val backgroundTextPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    fun setResults(boxes: List<BoundingBox>) {
        boundingBoxes.clear()
        boundingBoxes.addAll(boxes)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (box in boundingBoxes) {
            val color = if (box.isDanger) Color.RED else Color.GREEN
            boxPaint.color = color
            backgroundTextPaint.color = color
            
            // Draw Box
            val rect = RectF(box.x1, box.y1, box.x2, box.y2)
            canvas.drawRect(rect, boxPaint)
            
            // Draw Label Background
            val textWidth = textPaint.measureText(box.label)
            canvas.drawRect(
                box.x1, 
                box.y1 - 50f, 
                box.x1 + textWidth + 10f, 
                box.y1, 
                backgroundTextPaint
            )
            
            // Draw Label
            canvas.drawText(box.label, box.x1 + 5f, box.y1 - 10f, textPaint)
        }
    }
}

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classIndex: Int,
    val label: String,
    val isDanger: Boolean
)
