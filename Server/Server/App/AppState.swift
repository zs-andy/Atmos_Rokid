//
//  AppState.swift
//  Server
//
//  Created by GH on 11/23/25.
//

import SwiftUI

@Observable
class AppState {
    static var shared = AppState()
    
    var isServerRunning = false
    var serverStatus = "未启动"
    
    init() {
        Task {
            do {
                try await VaporServer.shared.start()
                await MainActor.run {
                    self.isServerRunning = true
                    self.serverStatus = "运行中 (端口 8080)"
                }
            } catch {
                await MainActor.run {
                    self.serverStatus = "启动失败: \(error.localizedDescription)"
                }
            }
        }
    }
}
