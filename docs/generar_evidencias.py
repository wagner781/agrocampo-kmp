"""Genera el PDF desde capturas e informes reales. Requiere reportlab y Pillow.
Ejecutar desde cualquier carpeta: python docs/generar_evidencias.py.
No ejecuta las pruebas ni crea capturas; exige que ya existan y hayan pasado.
"""
from pathlib import Path
from datetime import datetime, timezone, timedelta
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape
import json
import subprocess
import textwrap
from PIL import Image as PILImage
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, PageBreak, Table, TableStyle, Preformatted
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/evidencias'
BACK = ROOT.parent / 'agroapi-backend'
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='SmallNote', fontName='Helvetica', fontSize=9, leading=13, textColor=colors.HexColor('#526052')))
styles.add(ParagraphStyle(name='MonoSmall', fontName='Courier', fontSize=7.2, leading=10))
styles['Title'].textColor = colors.HexColor('#235C3B')
styles['Heading1'].textColor = colors.HexColor('#235C3B')
styles['BodyText'].leading = 15

def p(text, style='BodyText'):
    return Paragraph(escape(text), styles[style])

def foto(name, width=220, height=515):
    path = OUT / 'android' / name
    with PILImage.open(path) as im:
        w, h = im.size
    scale = min(width/w, height/h)
    return Image(str(path), w*scale, h*scale)

def pruebas(pattern):
    suites = [ET.parse(path).getroot() for path in pattern]
    if not suites:
        raise RuntimeError('No hay informes de prueba')
    total = sum(int(s.get('tests', 0)) for s in suites)
    fallos = sum(int(s.get('failures', 0)) + int(s.get('errors', 0)) for s in suites)
    if fallos:
        raise RuntimeError('El informe contiene pruebas fallidas')
    return total, fallos

backend = pruebas((BACK/'build/test-results/test').glob('TEST-*.xml'))
common = pruebas((ROOT/'shared/build/test-results/jvmTest').glob('TEST-*.xml'))
android = pruebas((ROOT/'androidApp/build/outputs/androidTest-results/connected/debug').rglob('TEST-*.xml'))
api = json.loads((OUT/'api-real.json').read_text(encoding='utf-8'))
ahora = datetime.now(timezone.utc).astimezone(timezone(timedelta(hours=-5))).isoformat(timespec='seconds')
story = []
story += [p('AgroSierra', 'Title'), p('Evidencias de una ejecucion verificada', 'Heading1'), p('Generado: '+ahora, 'SmallNote'), Spacer(1,20)]
story += [p('Backend: Spring Boot + Kotlin, Spring Security JWT y H2 en archivo. Aplicacion: Kotlin Multiplatform, Compose, Ktor, Koin y SQLDelight.'), Spacer(1,12)]
for text in [
    f'Backend: {backend[0]} pruebas de integracion, {backend[1]} fallos.',
    f'Codigo comun: {common[0]} pruebas con MockEngine y SQLite real en memoria, {common[1]} fallos.',
    f'Android: {android[0]} prueba instrumentada contra AgroAPI real, {android[1]} fallos.',
    'La prueba Android inicia sesion, sincroniza, activa modo avion, guarda una labor y recupera la conexion para enviarla.',
    'Las capturas siguientes provienen del emulador Pixel_10a con Android 17. No son maquetas. Las peticiones del API se ejecutaron contra localhost:8080.',
    'Las pruebas agregan registros reales de demostracion al servidor. Repetirlas genera otra operacion distinta; cada envio conserva su propia clave de idempotencia.',
    'El historial comienza el 28 de octubre de 2026. No acredita sesiones anteriores. La solicitud de cambio requiere asignacion del docente y la defensa corresponde al estudiante.'
]:
    story += [p(text), Spacer(1,10)]
story += [p('Repositorios privados: github.com/wagner781/agroapi-backend y github.com/wagner781/agrocampo-kmp.', 'SmallNote')]

story += [PageBreak(), p('1. Inicio de sesion y padron', 'Heading1')]
story += [p('El padron recibe datos del backend y los presenta desde la consulta observable local.'), Spacer(1,16)]
story += [Table([[foto('01-login.png'),foto('02-lotes.png')]], colWidths=[250,250]), Spacer(1,12),p('Izquierda: formulario real. Derecha: lotes descargados y almacenados con SQLDelight.', 'SmallNote')]

story += [PageBreak(), p('2. Registro sin conexion', 'Heading1')]
story += [p('El icono de avion corresponde al modo avion activado por la prueba. La labor queda marcada como pendiente y recibe un identificador negativo.'),Spacer(1,16)]
story += [Table([[foto('03-registro-offline.png'),foto('04-pendiente.png')]], colWidths=[250,250]),Spacer(1,12),p('La fila y la cola sobreviven al cierre del proceso porque se guardan en SQLite, no en una lista en memoria.', 'SmallNote')]

story += [PageBreak(),p('3. Recuperacion de la conexion','Heading1')]
story += [p('La misma sincronizacion puede activarse manualmente o mediante ConnectivityManager al recuperar una red validada.'),Spacer(1,12)]
story += [foto('05-sincronizada.png',270,560),Spacer(1,12),p('Se muestra el resumen real de una labor enviada y de las filas recibidas. commonTest verifica el reemplazo atomico del ID y que no se duplique la fila.', 'SmallNote')]

story += [PageBreak(),p('4. Contrato HTTP y pruebas','Heading1')]
rows = [['Metodo / ruta','HTTP']]+[[r['metodo']+' '+r['ruta'],str(r['status'])] for r in api['resultados']]
table = Table(rows,colWidths=[425,65])
table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#235C3B')),('TEXTCOLOR',(0,0),(-1,0),colors.white),('FONTSIZE',(0,0),(-1,-1),10),('BOTTOMPADDING',(0,0),(-1,-1),10),('TOPPADDING',(0,0),(-1,-1),10),('LINEBELOW',(0,0),(-1,-1),0.3,colors.lightgrey)]))
story += [table,Spacer(1,16),p('Rechazo real por falta de privilegio (administrador intentando registrar una labor):')]
rechazo = next(r for r in api['resultados'] if r['status']==403)
story += [Preformatted(json.dumps(rechazo['cuerpo'],ensure_ascii=False,indent=2),styles['MonoSmall']),Spacer(1,12)]
story += [p('Esta es una transcripcion JSON de la respuesta real, no una captura de una herramienta API. La coleccion Postman del backend permite reproducirla.'),Spacer(1,10)]
story += [p('Informes de prueba originales: backend build/test-results/test; shared/build/test-results/jvmTest; androidApp/build/outputs/androidTest-results. Se adjuntan resúmenes XML sin credenciales en esta carpeta.', 'SmallNote')]

for project in ['agroapi-backend','agrocampo-kmp']:
    graph = subprocess.check_output(['git','-C',str(ROOT.parent/project),'log','--graph','--oneline','--all'],text=True,encoding='utf-8')
    (OUT/(project+'-git.txt')).write_text(graph,encoding='utf-8')
    story += [PageBreak(),p('5. Git - '+project,'Heading1'),p('Salida real de git log --graph --oneline --all al generar este documento. Es una instantanea durante el desarrollo; las fusiones posteriores se consultan en GitHub.', 'SmallNote'),Spacer(1,12)]
    lines=[]
    for line in graph.splitlines():
        lines.extend(textwrap.wrap(line,width=105,subsequent_indent='    ') or [''])
    story += [Preformatted('\n'.join(lines),styles['MonoSmall'])]

def pie(canvas,doc):
    canvas.setStrokeColor(colors.HexColor('#235C3B'));canvas.line(42,40,553,40)
    canvas.setFont('Helvetica',8);canvas.drawString(42,27,'AgroSierra - evidencia tecnica real');canvas.drawRightString(553,27,str(doc.page))

OUT.mkdir(parents=True,exist_ok=True)
SimpleDocTemplate(str(OUT/'AgroSierra-evidencias.pdf'),pagesize=A4,rightMargin=42,leftMargin=42,topMargin=42,bottomMargin=52,
    title='AgroSierra - evidencias tecnicas',author='AgroSierra').build(story,onFirstPage=pie,onLaterPages=pie)
print(OUT/'AgroSierra-evidencias.pdf')
