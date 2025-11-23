//
//  YOLODetector.swift
//  YOLOTest
//
//  Created by GH on 11/22/25.
//

import AppKit
import CoreML

struct YOLODetector {
    private let iouThreshold: Float = 0.8
    private let confidenceThreshold: Float = 0.85
    
    private var yolo: YOLO12M?
    
    private let classList = [
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrus"
    ]
    
    init() {
        let configuration = MLModelConfiguration()
        guard let yolo = try? YOLO12M(configuration: configuration) else { return }
        self.yolo = yolo
    }
    
    func detect(image: NSImage) -> [YOLOResult] {
        guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return [] }
        guard let yolo  else { return [] }
        
        do {
            let input = try YOLO12MInput(imageWith: cgImage)
            let output = try yolo.prediction(input: input)
            return parseResult(output.var_2043)
        } catch {
            return []
        }
    }
    
    private func parseResult(_ result: MLMultiArray) -> [YOLOResult] {
        var detections: [YOLOResult] = []
        
        for i in 0..<8400 {
            let x = result[[0, 0, i] as [NSNumber]].floatValue
            let y = result[[0, 1, i] as [NSNumber]].floatValue
            let w = result[[0, 2, i] as [NSNumber]].floatValue
            let h = result[[0, 3, i] as [NSNumber]].floatValue
            
            var maxConfidence: Float = 0
            var maxClassIndex: Int = 0
            
            for classIndex in 0..<80 {
                let confidence = result[[0, 4 + classIndex, i] as [NSNumber]].floatValue
                if confidence > maxConfidence {
                    maxConfidence = confidence
                    maxClassIndex = classIndex
                }
            }
            
            if maxConfidence > confidenceThreshold {
                let className = maxClassIndex < classList.count ? classList[maxClassIndex] : "unknown"
                
                detections.append(YOLOResult(
                    x: x, y: y, width: w, height: h,
                    confidence: maxConfidence, className: className
                ))
            }
        }
        
        return applyNMS(detections: detections)
    }
    
    private func applyNMS(detections: [YOLOResult]) -> [YOLOResult] {
        let sortedDetections = detections.sorted { $0.confidence > $1.confidence }
        var finalDetections: [YOLOResult] = []
        
        for detection in sortedDetections {
            var shouldKeep = true
            
            for existing in finalDetections {
                if existing.className == detection.className {
                    let iou = calculateIoU(detection, existing)
                    if iou > iouThreshold {
                        shouldKeep = false
                        break
                    }
                }
            }
            
            if shouldKeep {
                finalDetections.append(detection)
            }
        }
        
        return finalDetections
    }
    
    private func calculateIoU(_ det1: YOLOResult, _ det2: YOLOResult) -> Float {
        let x1 = max(det1.x, det2.x)
        let y1 = max(det1.y, det2.y)
        let x2 = min(det1.x + det1.width, det2.x + det2.width)
        let y2 = min(det1.y + det1.height, det2.y + det2.height)
        
        let intersection = max(0, x2 - x1) * max(0, y2 - y1)
        let area1 = det1.width * det1.height
        let area2 = det2.width * det2.height
        let union = area1 + area2 - intersection
        
        return union > 0 ? intersection / union : 0
    }
}
