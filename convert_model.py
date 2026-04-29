# Convert YOLOv8 PyTorch model to TFLite
from ultralytics import YOLO

print("Loading PyTorch model...")
# Change this:
model = YOLO(r"D:\AndroidApp\best.pt")

print("Exporting model to TensorFlow Lite...")
# Note: int8 quantization is recommended for Android for better performance,
# but it requires a representative dataset. We'll stick to fp16/fp32 by default.
model.export(format='tflite')

print("Export complete! You will find the best_saved_model/best_float32.tflite or similar.")
print("Rename it to 'best.tflite' and place it in the d:\\AndroidApp\\app\\src\\main\\assets\\ directory.")
