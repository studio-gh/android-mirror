package com.studiogh.androidmirror

import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicReference

class MirrorServer(
    port: Int,
    private val latestJpeg: AtomicReference<ByteArray?>,
    private val screenW: Int,
    private val screenH: Int
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
        }
        return when (session.uri) {
            "/stream.mjpg" -> streamResponse()
            "/tap" -> {
                val x = session.parameters["x"]?.firstOrNull()?.toFloatOrNull()
                val y = session.parameters["y"]?.firstOrNull()?.toFloatOrNull()
                if (x != null && y != null) {
                    Handler(Looper.getMainLooper()).post {
                        MirrorAccessibilityService.performTap(
                            x.coerceIn(0f, 1f) * screenW,
                            y.coerceIn(0f, 1f) * screenH
                        )
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
                } else newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false}""")
            }
            "/key" -> {
                val key = session.parameters["key"]?.firstOrNull()
                if (key in setOf("back", "home", "recents")) {
                    Handler(Looper.getMainLooper()).post {
                        MirrorAccessibilityService.performGlobalActionFor(key!!)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
                } else newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false}""")
            }
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", HTML)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun streamResponse(): Response {
        val input = object : java.io.InputStream() {
            private var pending = ByteArray(0)
            private var index = 0
            override fun read(): Int {
                while (index >= pending.size) {
                    val jpeg = latestJpeg.get()
                    if (jpeg == null) { try { Thread.sleep(100) } catch (_: Exception) {}; continue }
                    pending = ("\r\n--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n").toByteArray() + jpeg
                    index = 0
                    try { Thread.sleep(65) } catch (_: Exception) {}
                }
                return pending[index++].toInt() and 0xff
            }
        }
        return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", input)
    }

    companion object {
        private val HTML = """
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Android Mirror</title><style>
:root{color-scheme:dark;--bg:#0c0d10;--panel:#15171c;--line:#2a2d35;--text:#f4f5f7;--muted:#a3a8b3;--accent:#c6f36b}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px system-ui,-apple-system,sans-serif}
header{padding:22px 26px;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;gap:16px}
.brand{font-size:12px;letter-spacing:.18em;color:var(--accent);font-weight:800}.title{font-size:21px;font-weight:700;margin-top:5px}
main{max-width:1120px;margin:0 auto;padding:24px;display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:20px}
.screen{background:#050505;border:1px solid var(--line);border-radius:18px;min-height:60vh;display:flex;align-items:center;justify-content:center;overflow:hidden}
img{display:block;max-width:100%;max-height:78vh;object-fit:contain;cursor:crosshair}
.side{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:18px;height:max-content}
.pill{display:inline-flex;padding:6px 9px;border:1px solid #4a6330;color:var(--accent);border-radius:999px;font-size:12px}
p{color:var(--muted);line-height:1.5;font-size:13px}.btns{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:14px}
button{background:#23262d;border:1px solid #363a44;border-radius:10px;color:white;padding:11px 8px;font:inherit;cursor:pointer}button:hover{border-color:var(--accent)}
.small{font-size:12px;color:var(--muted)}footer{padding:0 24px 24px;color:var(--muted);font-size:12px;max-width:1120px;margin:auto}
@media(max-width:760px){main{grid-template-columns:1fr;padding:12px}header{padding:18px}.screen{min-height:45vh}.side{order:2}}
</style></head><body>
<header><div><div class="brand">STUDIO GH · OPEN SOURCE</div><div class="title">Android Mirror</div></div><span class="pill" id="status">Conectando…</span></header>
<main><section class="screen"><img id="screen" src="/stream.mjpg" alt="Tela do Android. Mantenha a captura de tela autorizada no celular."></section>
<aside class="side"><h3 style="margin-top:0">Controles</h3><p>Clique na imagem para enviar um toque. O controle exige que o serviço de acessibilidade esteja ativado no Android.</p>
<div class="btns"><button onclick="key('back')">← Voltar</button><button onclick="key('home')">⌂ Início</button><button onclick="key('recents')">▣ Apps</button></div>
<p id="feedback" class="small">Aguardando imagem…</p><p>Conexão local. Não compartilhe este endereço com outras pessoas na sua rede.</p></aside></main>
<footer>O vídeo é transmitido pela rede Wi-Fi local. Para encerrar, toque em “Parar espelhamento” no Android.</footer>
<script>
const img=document.getElementById('screen'),status=document.getElementById('status'),feedback=document.getElementById('feedback');
img.onload=()=>{status.textContent='Espelhamento ativo';feedback.textContent='Tela recebida. Clique na imagem para tocar.'};
img.onerror=()=>{status.textContent='Sem imagem';feedback.textContent='Confira se a captura está ativa no Android.'};
img.addEventListener('click',async e=>{const r=img.getBoundingClientRect();const x=(e.clientX-r.left)/r.width,y=(e.clientY-r.top)/r.height;try{await fetch('/tap?x='+x+'&y='+y);feedback.textContent='Toque enviado';}catch(_){feedback.textContent='Não foi possível enviar o toque';}});
async function key(k){try{await fetch('/key?key='+k);feedback.textContent='Comando enviado';}catch(_){feedback.textContent='Não foi possível enviar o comando';}}
</script></body></html>
""".trimIndent()
    }
}
