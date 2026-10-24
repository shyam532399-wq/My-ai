import os
import asyncio
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from google import genai
from google.genai import types

app = FastAPI()
templates = Jinja2Templates(directory="templates")
client = genai.Client()

@app.get("/", response_class=HTMLResponse)
async def get_index(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})

@app.post("/api/chat")
async def text_chat(payload: dict):
    user_message = payload.get("message", "")
    try:
        response = client.models.generate_content(
            model="gemini-2.0-flash",
            contents=user_message,
            config=types.GenerateContentConfig(
                system_instruction="Aap ek smart, helpful, aur polite AI assistant hain."
            )
        )
        return {"response": response.text}
    except Exception as e:
        return {"error": str(e)}

@app.websocket("/ws/live")
async def live_audio_socket(websocket: WebSocket):
    await websocket.accept()
    model_id = "gemini-2.0-flash-exp"
    config = types.LiveConnectConfig(
        response_modalities=[types.LiveClientContentModality.AUDIO],
        speech_config=types.SpeechConfig(
            voice_config=types.VoiceConfig(
                prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name="Puck")
            )
        )
    )
    try:
        async with client.aio.live.connect(model=model_id, config=config) as gemini_session:
            async def receive_from_client():
                try:
                    while True:
                        data = await websocket.receive_bytes()
                        await gemini_session.send(input={"data": data, "mime_type": "audio/pcm"})
                except WebSocketDisconnect:
                    pass

            async def send_to_client():
                try:
                    async for response in gemini_session.receive():
                        server_content = response.server_content
                        if server_content and server_content.model_turn:
                            for part in server_content.model_turn.parts:
                                if part.inline_data:
                                    await websocket.send_bytes(part.inline_data.data)
                except Exception as e:
                    print("Live stream error:", e)

            await asyncio.gather(receive_from_client(), send_to_client())
    except Exception as e:
        print("WebSocket Error:", e)
    finally:
        await websocket.close()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
