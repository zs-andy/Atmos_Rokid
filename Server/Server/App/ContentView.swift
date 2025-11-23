//
//  ContentView.swift
//  Server
//
//  Created by GH on 11/23/25.
//

import SwiftUI

struct ContentView: View {
    @Bindable var imageManager = ImageManager.shared
    
    var body: some View {
        if let image = imageManager.image {
            Image(nsImage: image)
        }
    }
}

@Observable
class ImageManager {
    static var shared = ImageManager()
    
    var image: NSImage?
}

#Preview {
    ContentView()
}
