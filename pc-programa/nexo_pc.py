#!/usr/bin/env python3
"""Nexo PC: el escritorio de Nexo (en el teléfono) en una ventana del computador, por el cable USB-C a USB.

Usa el modo accesorio de Android (AOA, Android Open Accessory): el programa le pide al teléfono que se presente como
accesorio «Nexo / Escritorio»; Android abre Nexo, que manda la imagen (JPEG) y recibe el ratón y el teclado.
No necesita red, «Módem USB» ni depuración USB.

Mensajes por el cable: [tipo 1 byte][largo 4 bytes, big endian][datos]
  teléfono → PC: 1 = cuadro JPEG · 2 = información (JSON) · 4 = aviso (texto)
  PC → teléfono: 3 = ratón o teclado (JSON)

Requisitos: Python 3.9+, pip install pyusb libusb-package pillow
Windows: la primera vez instala el controlador WinUSB con Zadig (https://zadig.akeo.ie): Options › List All Devices,
escoge tu teléfono (y después «Escritorio», el accesorio) y pulsa «Replace Driver».
Linux: ejecuta con sudo o agrega una regla udev para tu teléfono y para 18d1:2d00/2d01.
"""
import io
import json
import queue
import struct
import sys
import threading
import time
import tkinter as tk

try:
    import usb.core
    import usb.util
except ImportError:
    print("Falta pyusb: pip install pyusb libusb-package pillow")
    raise
try:
    import libusb_package  # trae la biblioteca libusb para Windows, Mac y Linux
    BACKEND = libusb_package.get_libusb1_backend()
except Exception:  # sin libusb_package: la libusb del sistema
    BACKEND = None
from PIL import Image, ImageTk

GOOGLE = 0x18D1
ACCESORIO = range(0x2D00, 0x2D06)
CADENAS = ["Nexo", "Escritorio", "Escritorio de Nexo en el PC", "1.0",
           "https://github.com/maryangeld2026-hash/Voltix-apk/releases/tag/nexo-escritorio", "NexoPC0001"]
AYUDA_WINDOWS = ("En Windows hay que instalar una vez el controlador WinUSB con Zadig (zadig.akeo.ie): "
                 "Options › List All Devices, escoge el teléfono y pulsa «Replace Driver». Después, cuando aparezca "
                 "«Escritorio» (el teléfono en modo accesorio), repite con él.")


def buscar_accesorio():
    for pid in ACCESORIO:
        d = usb.core.find(idVendor=GOOGLE, idProduct=pid, backend=BACKEND)
        if d is not None:
            return d
    return None


def pasar_a_accesorio():
    """Pide a cada teléfono Android conectado que entre en modo accesorio. → True si alguno aceptó."""
    algun = False
    for d in usb.core.find(find_all=True, backend=BACKEND) or []:
        if d.bDeviceClass == 9:  # concentradores
            continue
        if d.idVendor == GOOGLE and d.idProduct in ACCESORIO:
            continue
        try:
            v = d.ctrl_transfer(0xC0, 51, 0, 0, 2, timeout=1000)
            version = v[0] | (v[1] << 8)
            if version < 1:
                continue
            for i, s in enumerate(CADENAS):
                d.ctrl_transfer(0x40, 52, 0, i, s.encode() + b"\0", timeout=1000)
            d.ctrl_transfer(0x40, 53, 0, 0, None, timeout=1000)
            algun = True
        except Exception:
            continue
    return algun


class Enlace:
    def __init__(self, dev):
        self.dev = dev
        try:
            if sys.platform.startswith("linux") and dev.is_kernel_driver_active(0):
                dev.detach_kernel_driver(0)
        except Exception:
            pass
        try:
            dev.set_configuration()
        except usb.core.USBError:
            pass
        cfg = dev.get_active_configuration()
        intf = cfg[(0, 0)]
        self.ep_in = usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_IN)
        self.ep_out = usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_OUT)
        self.vivo = True
        self.cuadros = queue.Queue(maxsize=2)
        self.mensajes = queue.Queue()
        self.lock = threading.Lock()
        threading.Thread(target=self._leer, daemon=True).start()

    def _leer(self):
        buf = bytearray()
        while self.vivo:
            try:
                buf += bytes(self.ep_in.read(16384 * 4, timeout=1000))
            except usb.core.USBTimeoutError:
                continue
            except Exception:
                self.vivo = False
                break
            while len(buf) >= 5:
                tipo = buf[0]
                largo = struct.unpack(">I", bytes(buf[1:5]))[0]
                if len(buf) < 5 + largo:
                    break
                datos = bytes(buf[5:5 + largo])
                del buf[:5 + largo]
                if tipo == 1:
                    if self.cuadros.full():
                        try:
                            self.cuadros.get_nowait()
                        except queue.Empty:
                            pass
                    self.cuadros.put(datos)
                else:
                    self.mensajes.put((tipo, datos.decode("utf-8", "replace")))

    def enviar(self, obj):
        if not self.vivo:
            return
        d = json.dumps(obj).encode()
        try:
            with self.lock:
                self.ep_out.write(bytes([3]) + struct.pack(">I", len(d)) + d, timeout=1000)
        except Exception:
            self.vivo = False


TECLAS = {"Return": "Enter", "KP_Enter": "NumpadEnter", "BackSpace": "Backspace", "Tab": "Tab", "Escape": "Escape",
          "Up": "ArrowUp", "Down": "ArrowDown", "Left": "ArrowLeft", "Right": "ArrowRight", "Delete": "Delete",
          "Home": "Home", "End": "End", "Prior": "PageUp", "Next": "PageDown", "Insert": "Insert",
          "Super_L": "MetaLeft", "Super_R": "MetaRight", "Win_L": "MetaLeft", "Win_R": "MetaRight", "space": "Space"}
TECLAS.update({f"F{i}": f"F{i}" for i in range(1, 13)})


class Ventana:
    def __init__(self):
        self.raiz = tk.Tk()
        self.raiz.title("Nexo PC")
        self.raiz.geometry("1280x760")
        self.raiz.configure(bg="#070a12")
        self.lienzo = tk.Canvas(self.raiz, bg="#070a12", highlightthickness=0, cursor="arrow")
        self.lienzo.pack(fill="both", expand=True)
        self.barra = tk.Label(self.raiz, text="Buscando el teléfono…", bg="#141a28", fg="#9aa3b8", anchor="w", padx=10)
        self.barra.pack(fill="x", side="bottom")
        self.enlace = None
        self.img = None
        self.caja = (0, 0, 1, 1)
        self.abajo = False
        self.ultimo_mov = 0
        self.pantalla_completa = False
        self.cuadros = 0
        self.t0 = time.time()
        c = self.lienzo
        c.bind("<ButtonPress-1>", lambda e: self.boton(e, "down", 0))
        c.bind("<ButtonRelease-1>", lambda e: self.boton(e, "up", 0))
        c.bind("<ButtonPress-3>", lambda e: self.boton(e, "down", 2))
        c.bind("<ButtonRelease-3>", lambda e: self.boton(e, "up", 2))
        c.bind("<B1-Motion>", self.mover)
        c.bind("<MouseWheel>", lambda e: self.rueda(e, -e.delta))
        c.bind("<Button-4>", lambda e: self.rueda(e, -120))
        c.bind("<Button-5>", lambda e: self.rueda(e, 120))
        self.raiz.bind("<KeyPress>", self.tecla)
        self.raiz.bind("<F11>", self.completa)
        self.raiz.after(200, self.ciclo)
        threading.Thread(target=self.conectar, daemon=True).start()

    def estado(self, t):
        self.raiz.after(0, lambda: self.barra.configure(text=t))

    def conectar(self):
        avisado = False
        while True:
            if self.enlace and self.enlace.vivo:
                time.sleep(1)
                continue
            try:
                d = buscar_accesorio()
                if d is None:
                    if pasar_a_accesorio():
                        self.estado("El teléfono está entrando en modo accesorio… acepta compartir la pantalla en Nexo.")
                        time.sleep(2.5)
                        continue
                    self.estado("Conecta el teléfono con el cable USB (con Nexo instalado)." + ("" if not sys.platform.startswith("win") else "  ·  " + AYUDA_WINDOWS))
                    time.sleep(1.5)
                    continue
                self.enlace = Enlace(d)
                self.estado("● Conectado. Si no ves nada, acepta «compartir la pantalla» en el teléfono y abre el escritorio allí.")
            except usb.core.NoBackendError:
                self.estado("No encontré libusb. Instala: pip install libusb-package")
                time.sleep(3)
            except Exception as e:  # permisos o controlador
                if not avisado:
                    print("Error USB:", e)
                    avisado = True
                self.estado("No pude abrir el teléfono por USB: " + str(e)[:80] + ("  ·  " + AYUDA_WINDOWS if sys.platform.startswith("win") else "  ·  En Linux ejecuta con sudo."))
                time.sleep(2)

    def ciclo(self):
        e = self.enlace
        if e:
            try:
                while True:
                    tipo, s = e.mensajes.get_nowait()
                    if tipo == 4:
                        self.barra.configure(text="Nexo: " + s)
            except queue.Empty:
                pass
            try:
                jpeg = e.cuadros.get_nowait()
                self.dibujar(jpeg)
            except queue.Empty:
                pass
            if not e.vivo:
                self.enlace = None
                self.barra.configure(text="Se desconectó el teléfono. Vuelve a conectar el cable.")
        self.raiz.after(10, self.ciclo)

    def dibujar(self, jpeg):
        try:
            im = Image.open(io.BytesIO(jpeg))
        except Exception:
            return
        W = max(1, self.lienzo.winfo_width())
        H = max(1, self.lienzo.winfo_height())
        k = min(W / im.width, H / im.height)
        w, h = max(1, int(im.width * k)), max(1, int(im.height * k))
        if (w, h) != im.size:
            im = im.resize((w, h), Image.BILINEAR)
        x0, y0 = (W - w) // 2, (H - h) // 2
        self.caja = (x0, y0, w, h)
        self.img = ImageTk.PhotoImage(im)
        self.lienzo.delete("all")
        self.lienzo.create_image(x0, y0, anchor="nw", image=self.img)
        self.cuadros += 1
        if time.time() - self.t0 >= 1:
            self.barra.configure(text=f"● Conectado · {self.cuadros} cuadros/s  ·  F11 pantalla completa  ·  tecla Windows: menú de inicio")
            self.cuadros = 0
            self.t0 = time.time()

    def pos(self, e):
        x0, y0, w, h = self.caja
        return {"x": min(1, max(0, (e.x - x0) / w)), "y": min(1, max(0, (e.y - y0) / h))}

    def boton(self, e, t, b):
        self.lienzo.focus_set()
        if self.enlace:
            self.enlace.enviar(dict(self.pos(e), t=t, b=b))

    def mover(self, e):
        now = time.time()
        if now - self.ultimo_mov < 0.016 or not self.enlace:
            return
        self.ultimo_mov = now
        self.enlace.enviar(dict(self.pos(e), t="move"))

    def rueda(self, e, dy):
        if self.enlace:
            self.enlace.enviar(dict(self.pos(e), t="wheel", dy=dy))

    def tecla(self, e):
        if not self.enlace or e.keysym == "F11":
            return
        ctrl = bool(e.state & 0x4)
        alt = bool(e.state & 0x20000) or bool(e.state & 0x8 and not sys.platform.startswith("win"))
        code = TECLAS.get(e.keysym, "")
        if not code and len(e.keysym) == 1 and e.keysym.isalpha():
            code = "Key" + e.keysym.upper()
        if not code and e.keysym.isdigit():
            code = "Digit" + e.keysym
        ch = e.char if (e.char and ord(e.char[0]) >= 32 and not ctrl) else ""
        if not code and not ch:
            return
        self.enlace.enviar({"t": "key", "code": code, "key": ch or e.keysym, "ctrl": ctrl, "shift": bool(e.state & 0x1), "alt": alt, "meta": False})
        return "break"

    def completa(self, _e=None):
        self.pantalla_completa = not self.pantalla_completa
        self.raiz.attributes("-fullscreen", self.pantalla_completa)
        return "break"


if __name__ == "__main__":
    Ventana().raiz.mainloop()
