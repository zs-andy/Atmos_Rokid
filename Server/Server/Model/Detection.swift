//
//  Detection.swift
//  Server
//
//  Created by GH on 11/23/25.
//

import Vapor

struct DetectionResponse: Content {
    let result: String
    let audio: String
}
