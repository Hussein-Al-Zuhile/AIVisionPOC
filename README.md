# AI Vision POC

## About the Project

The **AI Vision POC** Android application is a proof of concept for running a **multimodal LLM fully on-device**
(offline) using [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), to ask questions about a text prompt or an
image.

### Key Features:

1. Pick an image from the gallery or capture it by the camera.
2. Write a prompt and generate an answer from the text only, or from the text with the image.
3. Stream the answer and render it as Markdown, with auto-scroll and the ability to stop the generation.
4. Choose between two Gemma models:
    - `FAST`: Gemma 4 E2B (~2.6 GB).
    - `THINKING`: Gemma 4 E4B (~3.7 GB).
5. Download the selected model from Hugging Face by a foreground service with a progress notification, and cancel it.

---

## Project Structure

The project is a single screen app with a `ViewModel`, all the files are in
[`com.tatweer.aivisionpoc`](app/src/main/java/com/tatweer/aivisionpoc):

- [`MainActivity`](app/src/main/java/com/tatweer/aivisionpoc/MainActivity.kt): Entry point that renders the `AiPocScreen`.
- [`AiPocScreen`](app/src/main/java/com/tatweer/aivisionpoc/AiPocScreen.kt): The screen, split into composable functions (image preview, image source buttons, model selector, download banner, generate buttons and the response section).
- [`AiViewModel`](app/src/main/java/com/tatweer/aivisionpoc/AiViewModel.kt): Manages the screen state and handles the `AiUiEvent`s.
- [`AiHelper`](app/src/main/java/com/tatweer/aivisionpoc/AiHelper.kt): Wraps the LiteRT-LM `Engine`, defines the `ModelVariant`s, downloads the models and generates the answers (text or text with image).
- [`DownloadService`](app/src/main/java/com/tatweer/aivisionpoc/DownloadService.kt): Foreground service that downloads the model and shows the progress notification.

---

## Branches

- `main`: The app with LiteRT-LM directly.
- `feature/koog-integration` _**(Latest work)**_: Integrates [Koog](https://github.com/JetBrains/koog) agents with the
  local LiteRT model (`KoogHelper`), replaces the single run with a two-stage subgraph pipeline (distill then answer),
  and adds voice input for the prompt. It raises the `minSdk` to 33.

---

### Notes:

- The models are not bundled with the app, download them from the app first, they are saved in the app's internal
  storage (`filesDir/llm`).
- The engine runs on the CPU backend with a maximum of 2048 tokens and one image, so the generation is slow on the
  weak devices.

---
