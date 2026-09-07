# Whisper-Groq

Teclado virtual (IME) + serviço de reconhecimento de voz para Android, com transcrição via **API Whisper da Groq** — rápido, sem modelo local, sem necessidade de GPU.

Baseado em [whisperIMEplus](https://github.com/woheller69/whisperIMEplus) por woheller69 (GPLv3).

## Funcionalidades

- **IME (teclado por voz)**: toca no microfone para gravar, toca de novo para parar — a transcrição é inserida direto no campo de texto
- **RecognitionService**: selecionável como entrada de voz padrão do sistema
- **RecognizerIntent**: outros apps podem chamar via `ACTION_RECOGNIZE_SPEECH`
- **Tap-to-record** (sem segurar), limite configurável (5–300s, default 60s)
- **Botões estáveis**: `. , ! ? ␣ Backspace Enter` na mesma linha, todos com tamanho uniforme
- **Idioma auto-detectado** pela API Whisper
- **API key criptografada** com Android Keystore (AES-256)
- **Tema claro** no app principal / escuro no IME para contraste sobre outros apps

## Instalação

1. Instale o APK da aba Releases
2. Abra **Whisper-Groq** — conceda permissão de microfone
3. Vá em **Configurações** (menu) e cole sua **API key da Groq** de https://console.groq.com
4. Escolha o modelo Whisper (padrão: `whisper-large-v3-turbo`)
5. Ative o **Whisper-Groq** em *Configurações → Sistema → Idiomas → Teclado virtual*

## Modelos Whisper

- `whisper-large-v3-turbo` (padrão) — rápido e preciso, multilíngue
- `whisper-large-v3` — máxima qualidade, multilíngue
- `distil-whisper-large-v3-en` — só inglês, mais rápido

## Requisitos

- Internet
- API key Groq (grátis em console.groq.com)

## Privacidade

- O áudio é gravado apenas quando você toca no microfone
- Cada gravação é enviada via HTTPS POST stateless para `api.groq.com` — sem sessão, sem cookie
- A API key fica criptografada localmente; nada sai do dispositivo além do áudio transcrito

## Build

APK assinado é gerado pelo GitHub Actions quando uma tag `v*` é empurrada.

## Licença

GPLv3 — veja LICENSE. Original: © woheller69
