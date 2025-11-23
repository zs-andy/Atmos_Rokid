import Vapor
import AppKit
import CoreImage
import MLXLMCommon

class VaporServer {
    static let shared = VaporServer()
    
    var app: Application?
    private var fastVLM: FastVLMModel?
    
    private init() {}
    
    func start() async throws {
        await MainActor.run {
            self.fastVLM = FastVLMModel()
        }
        
        var env = try! Environment.detect()
        try! LoggingSystem.bootstrap(from: &env)
        
        let app = try await Application.make(env)
        
        configure(app)
        
        do {
            try await app.startup()
            print("Vapor Server Started on port \(app.http.server.configuration.port)")
        } catch {
            print("Failed to start Vapor server: \(error)")
        }
        
        self.app = app
    }
    
    func stop() {
        app?.shutdown()
        app = nil
        print("Vapor Server Stopped")
    }
    
    private func configure(_ app: Application) {
        app.http.server.configuration.port = 8080
        app.http.server.configuration.hostname = "0.0.0.0"
        
        app.on(.POST, "yolo", body: .collect(maxSize: "10mb")) { request async throws -> YOLOResponse in
            let file = try request.content.get(File.self, at: "file")
            let data = Data(file.data.readableBytesView)
            
            guard let nsImage = NSImage(data: data) else {
                throw Abort(.badRequest, reason: "数据无法转换为 NSImage")
            }
            ImageManager.shared.image = nsImage
            
            let yoloResults = YOLODetector().detect(image: nsImage)
            
            let result = YOLOResponse(result: yoloResults)
            return result
        }
        
        app.on(.POST, "detect", body: .collect(maxSize: "10mb")) { req async throws -> DetectionResponse in
            let file = try req.content.get(File.self, at: "file")
            let data = Data(file.data.readableBytesView)
            
            guard let nsImage = NSImage(data: data) else {
                throw Abort(.badRequest, reason: "数据无法转换为 NSImage")
            }
            
            var fastVLMOutput = ""
            
            let fastVLM = await MainActor.run {
                if self.fastVLM == nil {
                    self.fastVLM = FastVLMModel()
                }
                return self.fastVLM!
            }
            
            guard let cgImage = nsImage.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
                throw Abort(.internalServerError, reason: "无法获取 CGImage")
            }
            
            let ciImage = CIImage(cgImage: cgImage)
            
            let userInput = UserInput(
                prompt: .text("用中文描述画面中的内容。要求：如果有人，需描述人物的情绪；如果没有，需要重点描述图片的氛围。不要超过 20 字"),
                images: [.ciImage(ciImage)]
            )
            
            let task = await Task { @MainActor in
                await fastVLM.generate(userInput)
            }.value
            await task.value
            
            fastVLMOutput = await MainActor.run {
                fastVLM.output
            }
            
            let baseURL = URI(string: "https://api-bj.minimaxi.com/v1/t2a_v2")
            let headers: HTTPHeaders = [
//                "Authorization": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJHcm91cE5hbWUiOiLmtbfonrrnlKjmiLdfNDQ4ODc1NTg0NjU4NzU1NTg5IiwiVXNlck5hbWUiOiLmtbfonrrnlKjmiLdfNDQ4ODc1NTg0NjU4NzU1NTg5IiwiQWNjb3VudCI6IiIsIlN1YmplY3RJRCI6IjE5OTIwNDE2NTYyOTkyOTUyNTMiLCJQaG9uZSI6IjE4ODAwMzY4MTg3IiwiR3JvdXBJRCI6IjE5OTIwNDE2NTYyOTUxMDA5NDkiLCJQYWdlTmFtZSI6IiIsIk1haWwiOiIiLCJDcmVhdGVUaW1lIjoiMjAyNS0xMS0yMyAxMzo0NTo1NiIsIlRva2VuVHlwZSI6MSwiaXNzIjoibWluaW1heCJ9.T6vSyWBfV_1pUztDTQSu5TbtAuZVOtyUX5Ta3AViJQHY-ClYTCi2iTorlTymNqCvh1kRuOjSN7PFUvCe8UCHtt2yU2DVlq6tlXqH9-xWKKE7eyiE15BdzC01CUcazbNwckSRc7w6uyEpUefjNMcfADP--GlId9FKUt8NIXS56ohYsIlW9XQlQw9Qzx2WOshgCxFQLCPRNIG0_J-goKwXh_hZA4dLZ-BWUc9TfsMqSm42pRMM0Jr1M-1bbBoL6bzL1-doKXQTuFDh0AyYw9jWyKKhyd6cSH3T9N_xXyoRZuamlYpyymy21lD02cSpOtueN_OWjuUaxbagKEIbIVW-mg",
                "Authorization": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJHcm91cE5hbWUiOiLkuIrmtbfnrZHmoqbnqbrpl7Tnp5HmioDmnInpmZDlhazlj7giLCJVc2VyTmFtZSI6IumZiOaWh-adsCIsIkFjY291bnQiOiIiLCJTdWJqZWN0SUQiOiIxODk2NzY5MTY1OTM1NTEzNjIzIiwiUGhvbmUiOiIxODkzMDMwNDk1MSIsIkdyb3VwSUQiOiIxODk2NzY5MTY1OTIyOTMwNzExIiwiUGFnZU5hbWUiOiIiLCJNYWlsIjoiIiwiQ3JlYXRlVGltZSI6IjIwMjUtMDctMjAgMjA6Mjk6NTkiLCJUb2tlblR5cGUiOjEsImlzcyI6Im1pbmltYXgifQ.X3qypIoCY5iWqMWbhrWhxmseVDAtfWNhzZyxPEY2SH4GYnYSQU2Zl7t7BwTDeEKdUfCrGNQk26Lt4hd4JU27RO5b3IDZ9Rj7Q63e_Z-upH4x26TgInMPTItVlUvt1sjN2nzugKppELTgaqfpLsRh9AaRbCNCSwLrWyerLoex2M6_1tOiZ8nPFh1cn1bbfYt0IzrDsjkdZFI8F9fQWN0cUJPfQJLwKyuhQQDPnWCeCQyCzMq6Rf0FfunXOcA2MmmVQz_BzI8h8hbHZSOaz8OCySd_yRRY11V6Pw2VOAzggAtu5UeP68iWZUo-obpMet5dYRCi_4P3hI3VJ2Yl1wxq8g",
                "Content-Type": "application/json"
            ]
            
            
            let minimaxRequest = MiniMaxRequest(
                model: "speech-2.6-turbo",
                text: fastVLMOutput,
                voice_setting: .init(voice_id: "female-shaonv-jingpin")
            )
            
            let response = try await req.client.post(baseURL, headers: headers, content: minimaxRequest)
            
            let minimaxResult = try response.content.decode(MiniMaxResponse.self)

            let hexAudioString = minimaxResult.data.audio
            var base64Audio = ""
            if let audioData = hexAudioString.hexData {
                base64Audio = audioData.base64EncodedString()
            }
            
            let result = DetectionResponse(result: fastVLMOutput, audio: base64Audio)

            return result
        }
    }
}

struct MiniMaxResponse: Content {
    let data: MiniMaxAudio
    
    struct MiniMaxAudio: Content {
        let audio: String
    }
}

struct MiniMaxRequest: Content {
    let model: String
    let text: String
    let voice_setting: VoiceSetting
    
    struct VoiceSetting: Content {
        let voice_id: String
    }
}

extension String {
    /// 将十六进制字符串转换为 Data
    var hexData: Data? {
        var data = Data(capacity: count / 2)
        
        let regex = try! NSRegularExpression(pattern: "[0-9a-f]{1,2}", options: .caseInsensitive)
        regex.enumerateMatches(in: self, range: NSRange(startIndex..., in: self)) { match, _, _ in
            let byteString = (self as NSString).substring(with: match!.range)
            if let num = UInt8(byteString, radix: 16) {
                data.append(num)
            }
        }
        
        guard data.count > 0 else { return nil }
        return data
    }
}
