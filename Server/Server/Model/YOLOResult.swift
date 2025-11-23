//
//  YOLOResult.swift
//  YOLOTest
//
//  Created by GH on 11/22/25.
//

import Vapor

struct YOLOResponse: Content {
    let result: [YOLOResult]
}

struct YOLOResult: Content {
    let x: Float
    let y: Float
    let width: Float
    let height: Float
    let confidence: Float
    let className: String
    
    enum CodingKeys: String, CodingKey {
        case x
        case y
        case width = "w"
        case height = "h"
        case confidence
        case className = "class_name"
    }
}
