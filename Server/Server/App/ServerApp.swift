//
//  ServerApp.swift
//  Server
//
//  Created by GH on 11/23/25.
//

import SwiftUI

@main
struct ServerApp: App {
    @Bindable var appState = AppState.shared
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }

        MenuBarExtra("Atmos Server", systemImage: "server.rack") {
            MenuBarView()
        }
        .menuBarExtraStyle(.window)
    }
}

struct MenuBarView: View {
    @Bindable var appState = AppState.shared
    
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Circle()
                    .fill(appState.isServerRunning ? Color.green : Color.red)
                    .frame(width: 8, height: 8)
                Text(appState.serverStatus)
                    .font(.headline)
            }
            .padding()
            
            Divider()
            
            Button("退出") {
                NSApplication.shared.terminate(nil)
            }
            .keyboardShortcut("q")
        }
        .frame(width: 200)
        .padding()
    }
}
