package pe.upeu.agrosierra.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.presentation.*

private val verde=Color(0xFF235C3B)
@Composable fun AgroCampo(vm: CampoViewModel) {
    val p by vm.pantalla.collectAsState()
    MaterialTheme(colorScheme=lightColorScheme(primary=verde,secondary=Color(0xFF5D7047),background=Color(0xFFF7F8F2))) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("AGROSIERRA",style=MaterialTheme.typography.labelLarge,color=verde)
                Text(if(p.sesion==null) "AgroCampo" else if(p.perfil) "Mi perfil" else if(p.formulario!=null) "Registrar / corregir labor" else p.lote?.nombre ?: "Lotes de campo",
                    style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                if(p.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(p.mensaje.isNotBlank()) {
                    Card(colors=CardDefaults.cardColors(containerColor=if(p.error==null) Color(0xFFE2EEDD) else Color(0xFFFFEADD))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.mensaje)
                            if(p.error is ErrorApi.Servidor || p.error is ErrorApi.TiempoAgotado || p.error is ErrorApi.SinConexion)
                                TextButton(onClick={ vm.sync() },enabled=!p.ocupado && p.sesion!=null) { Text("Reintentar sincronizacion") }
                            TextButton(onClick=vm::limpiarMensaje) { Text("Entendido") }
                        }
                    }
                }
                if(p.sesion==null) Login(p,vm)
                else {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(p.lote!=null || p.perfil) OutlinedButton(onClick=vm::volver,enabled=!p.ocupado) { Text("Volver") }
                        if(!p.perfil && p.formulario==null) OutlinedButton(onClick=vm::perfil) { Text("Perfil") }
                        if(p.formulario==null) Button(onClick=vm::sync,enabled=!p.ocupado) { Text("Sincronizar") }
                    }
                    when {
                        p.perfil -> Perfil(p,vm)
                        p.formulario!=null -> Formulario(p,vm)
                        p.lote!=null -> Detalle(p,vm)
                        else -> Listado(p,vm)
                    }
                }
            }
        }
    }
}
@Composable private fun Login(p: Pantalla,vm: CampoViewModel) {
    var usuario by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Sus labores, disponibles tambien sin conexion",style=MaterialTheme.typography.bodyLarge)
        OutlinedTextField(usuario,{usuario=it},label={Text("Usuario")},singleLine=true,modifier=Modifier.fillMaxWidth(),
            isError=p.campos.containsKey("usuario"),supportingText={p.campos["usuario"]?.let {Text(it)}})
        OutlinedTextField(password,{password=it},label={Text("Contrasena")},singleLine=true,modifier=Modifier.fillMaxWidth(),
            visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
        Button(onClick={vm.login(usuario,password)},enabled=!p.ocupado,modifier=Modifier.fillMaxWidth()) { Text("Iniciar sesion") }
        Text("Necesita conexion para iniciar sesion. Una sesion vigente permite trabajar con los datos ya sincronizados.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun Listado(p: Pantalla,vm: CampoViewModel) {
    Text("Ultima sincronizacion: ${p.ultimaSync}",style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(p.buscar,vm::buscar,label={Text("Buscar por nombre o codigo")},singleLine=true,modifier=Modifier.fillMaxWidth())
    SelectorValle(p.valle,vm::valle)
    when(p.estado) {
        UiState.Cargando -> CircularProgressIndicator()
        UiState.Vacio -> Text("No hay lotes para estos filtros. Sincronice para obtener el padron.")
        is UiState.Error -> Text("No se pudieron cargar los lotes")
        UiState.Contenido -> Unit
    }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        items(p.lotes,key={it.id}) { lote ->
            Card(onClick={vm.detalle(lote)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(lote.codigo,style=MaterialTheme.typography.labelLarge,color=verde)
                    Text(lote.nombre,style=MaterialTheme.typography.titleLarge)
                    Text("${lote.valle} · ${lote.cultivo} · ${lote.hectareas} ha")
                    Text(if(lote.campaniaCerrada) "Campania cerrada" else "Campania abierta",fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
@Composable private fun SelectorValle(valor: String,onChange: (String)->Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={abierto=true}) {Text(if(valor.isBlank()) "Todos los valles" else "Valle: $valor")}
        DropdownMenu(expanded=abierto,onDismissRequest={abierto=false}) {
            listOf("","Mantaro","Sagrado","Colca").forEach { v ->
                DropdownMenuItem(text={Text(v.ifBlank { "Todos los valles" })},onClick={onChange(v);abierto=false})
            }
        }
    }
}
@Composable private fun Detalle(p: Pantalla,vm: CampoViewModel) {
    val l=p.lote ?: return; val privilegios=p.sesion?.privilegios ?: emptySet()
    var confirmar by remember { mutableStateOf<Labor?>(null) }
    var confirmarCierre by remember { mutableStateOf(false) }
    if(confirmarCierre) AlertDialog(onDismissRequest={confirmarCierre=false},title={Text("Cerrar campania")},
        text={Text("El servidor comprobara que exista una cosecha. Despues del cierre no se admiten registros ni correcciones.")},
        confirmButton={TextButton(onClick={confirmarCierre=false;vm.cerrar()}) {Text("Cerrar campania")}},
        dismissButton={TextButton(onClick={confirmarCierre=false}) {Text("Cancelar")}})
    if(confirmar!=null) AlertDialog(onDismissRequest={confirmar=null},title={Text("Eliminar labor")},text={Text("La eliminacion se enviara al sincronizar.")},
        confirmButton={TextButton(onClick={confirmar?.let(vm::eliminar);confirmar=null}) {Text("Eliminar")}},
        dismissButton={TextButton(onClick={confirmar=null}) {Text("Cancelar")}})
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("${l.codigo} · ${l.valle} · ${l.hectareas} ha · ${l.cultivo}")
            Text("Dosis maxima: ${l.dosisMaximaPorHectarea} por ha")
            Text(if(l.campaniaCerrada) "Campania cerrada" else "Campania abierta",fontWeight=FontWeight.Bold)
            if(!l.campaniaCerrada && "LABOR_CREAR" in privilegios) Button(onClick={vm.editar()},enabled=!p.ocupado) {Text("Registrar labor")}
            if(!l.campaniaCerrada && "CAMPANIA_CERRAR" in privilegios) OutlinedButton(onClick={confirmarCierre=true},enabled=!p.ocupado) {Text("Cerrar campania")}
            Text("Labores",style=MaterialTheme.typography.titleLarge)
            if(p.estadoLabores==UiState.Cargando) CircularProgressIndicator()
            if(p.estadoLabores==UiState.Vacio) Text("Este lote aun no tiene labores guardadas")
        }
        items(p.labores,key={it.id}) { labor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${labor.tipo} · ${labor.fecha}",style=MaterialTheme.typography.titleMedium)
                    Text("${labor.responsable} · cantidad ${labor.cantidad}")
                    if(labor.observacion.isNotBlank()) Text(labor.observacion)
                    if(labor.pendiente) Text("Pendiente de sincronizar · ID ${labor.id}",color=Color(0xFF985100),fontWeight=FontWeight.Bold)
                    Row {
                        if(!l.campaniaCerrada && "LABOR_EDITAR" in privilegios)
                            TextButton(onClick={vm.editar(labor)},enabled=!p.ocupado) {Text("Corregir")}
                        if("LABOR_ELIMINAR" in privilegios && labor.id>0)
                            TextButton(onClick={confirmar=labor},enabled=!p.ocupado) {Text("Eliminar")}
                    }
                }
            }
        }
    }
}
@Composable private fun Formulario(p: Pantalla,vm: CampoViewModel) {
    val original=p.formulario ?: return
    // Se mantiene el borrador cuando el servidor devuelve errores por campo.
    var tipo by remember { mutableStateOf(original.tipo) }
    var fecha by remember { mutableStateOf(original.fecha) }
    var responsable by remember { mutableStateOf(original.responsable) }
    var cantidad by remember { mutableStateOf(if(original.cantidad>0) original.cantidad.toString() else "") }
    var observacion by remember { mutableStateOf(original.observacion) }
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        var tipos by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick={tipos=true}) {Text("Tipo: $tipo")}
            DropdownMenu(tipos,{tipos=false}) { TipoLabor.entries.forEach { t -> DropdownMenuItem(text={Text(t.name)},onClick={tipo=t;tipos=false}) } }
        }
        Campo("fecha","Fecha (AAAA-MM-DD)",fecha,{fecha=it},p.campos)
        Campo("responsable","Responsable",responsable,{responsable=it},p.campos)
        Campo("cantidad","Cantidad aplicada",cantidad,{cantidad=it},p.campos,KeyboardType.Decimal)
        Campo("observacion","Observacion",observacion,{observacion=it},p.campos)
        Button(onClick={vm.guardarFormulario(original.copy(tipo=tipo,fecha=fecha,responsable=responsable,
            cantidad=cantidad.replace(',','.').toDoubleOrNull() ?: Double.NaN,observacion=observacion))},enabled=!p.ocupado,modifier=Modifier.fillMaxWidth()) {Text("Guardar labor")}
    }
}
@Composable private fun Campo(nombre: String,label: String,valor: String,cambiar: (String)->Unit,errores: Map<String,String>,tipo: KeyboardType=KeyboardType.Text) {
    OutlinedTextField(valor,cambiar,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=nombre!="observacion",
        keyboardOptions=KeyboardOptions(keyboardType=tipo),isError=errores.containsKey(nombre),supportingText={errores[nombre]?.let {Text(it)}})
}
@Composable private fun Perfil(p: Pantalla,vm: CampoViewModel) {
    val s=p.sesion ?: return
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(s.usuario,style=MaterialTheme.typography.titleLarge)
        Text("Rol: ${s.rol}")
        Text("Privilegios",fontWeight=FontWeight.Bold)
        s.privilegios.sorted().forEach { Text(it) }
        OutlinedButton(onClick=vm::actualizarPerfil,enabled=!p.ocupado) {Text("Actualizar perfil")}
        Button(onClick=vm::salir,enabled=!p.ocupado) {Text("Cerrar sesion")}
        Text("Cerrar sesion elimina el token. Los pendientes locales se conservan para la misma cuenta.",style=MaterialTheme.typography.bodySmall)
    }
}
