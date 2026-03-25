package ovh.gabrielhuav.sensores_escom_v2.presentation.locations.outdoor.locations.esime.buildings



import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import ovh.gabrielhuav.sensores_escom_v2.R
import ovh.gabrielhuav.sensores_escom_v2.data.map.OnlineServer.OnlineServerManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.common.managers.MovementManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.common.managers.ServerConnectionManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.mapview.MapMatrixProvider
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.mapview.MapView
import ovh.gabrielhuav.sensores_escom_v2.presentation.locations.outdoor.Esime
import ovh.gabrielhuav.sensores_escom_v2.presentation.locations.outdoor.esime.buildings.MinijuegoEnergiaActivity

class Edificio3Activity : AppCompatActivity(), OnlineServerManager.WebSocketListener, MapView.MapTransitionListener {

    private lateinit var movementManager: MovementManager
    private lateinit var serverConnectionManager: ServerConnectionManager
    private lateinit var mapView: MapView

    // 🎒 "Inventario" sencillo para los puzzles
    private var tieneFusible = false
    private var tieneContrasena = false
    private var energiaRestaurada = false
    private lateinit var btnNorth: MaterialButton
    private lateinit var btnSouth: MaterialButton
    private lateinit var btnEast: MaterialButton
    private lateinit var btnWest: MaterialButton
    private lateinit var buttonA: MaterialButton
    private lateinit var btnBack: MaterialButton

    private lateinit var playerName: String
    private var isServer: Boolean = false
    private var canExitBuilding = false

    // 🟢 POSICIÓN INICIAL SEGURA EN EL PASILLO
    private var playerPosition: Pair<Int, Int> = Pair(3, 19)

    private val collisionMap: Array<BooleanArray> by lazy { createCollisionMap() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esime)

        try {
            initializeViews()

            mapView = MapView(context = this, mapResourceId = R.drawable.mapa_edificio3_esime)
            findViewById<FrameLayout>(R.id.map_container).addView(mapView)

            playerName = intent.getStringExtra("PLAYER_NAME") ?: "Jugador"
            isServer = intent.getBooleanExtra("IS_SERVER", false)

            mapView.post {
                mapView.setCurrentMap("edificio3", R.drawable.mapa_edificio3_esime)
                mapView.playerManager.apply {
                    setCurrentMap("edificio3")
                    localPlayerId = playerName
                    updateLocalPlayerPosition(playerPosition)
                }
                initializeManagers()
                setupButtonListeners()
            }
        } catch (e: Exception) {
            Log.e("Edificio3", "Error: ${e.message}")
            finish()
        }
    }

    private fun createCollisionMap(): Array<BooleanArray> {
        val cols = 40
        val rows = 40
        val collisions = Array(cols) { BooleanArray(rows) { false } }

        // 1. LIMITES EXTERNOS (Para que no salgas del mapa negro)
        for (x in 0 until cols) {
            collisions[x][0] = true; collisions[x][39] = true
        }
        for (y in 0 until rows) {
            collisions[0][y] = true; collisions[39][y] = true
        }

        // 2. MUROS HORIZONTALES LARGOS
        for (x in 3..37) {
            collisions[x][7] = true  // Techo de aulas superiores
            collisions[x][18] = true // Muro superior del pasillo
            collisions[x][22] = true // Muro inferior del pasillo
            collisions[x][32] = true // Suelo de aulas inferiores
        }

        // 3. MUROS VERTICALES (Divisores de Aulas)
        // Inferiores
        for (y in 23..32) {
            collisions[8][y] = true  // Izquierda Aula 7
            collisions[15][y] = true // Entre Aula 7 y 8
            collisions[22][y] = true // Entre Aula 8 y 9
            collisions[30][y] = true // Derecha Aula 9
        }

        // Superiores
        for (y in 7..18) {
            collisions[7][y] = true  // Izquierda Aula 2 (Pared doble)
            collisions[8][y] = true  // Izquierda Aula 2 (Pared doble)
            collisions[13][y] = true // Entre Aula 2 y 3

            collisions[19][y] = true // Entre Aula 3 y 4

            collisions[25][y] = true // Entre Aula 4 y 5
        }
        for (y in 7..17) {
            collisions[31][y] = true // Derecha Aula 5
        }

        // 4. ABRIR EL VESTÍBULO (Pasillo vertical a la izquierda)
        for (x in 3..7) {
            collisions[x][17] = false
            collisions[x][18] = false // Romper el muro superior del pasillo aquí
            collisions[x][22] = false // Romper el muro inferior del pasillo aquí
        }

        // 5. PUERTAS EXACTAS (Huecos en los muros)
        // Puertas Superiores (Y = 18)
        collisions[8][18] = false; collisions[9][18] = false   // Aula 2
        collisions[14][18] = false; collisions[15][18] = false // Aula 3
        collisions[20][18] = false                             // Aula 4
        collisions[26][18] = false                             // Aula 5

        // Puertas Inferiores (Y = 22)
        collisions[10][22] = false                             // Aula 7
        collisions[17][22] = false; collisions[18][22] = false // Aula 8
        collisions[24][22] = false; collisions[25][22] = false // Aula 9

        return collisions
    }

    private fun getRoomName(position: Pair<Int, Int>): String {
        val (x, y) = position

        // 1. ZONAS DE PASILLO
        if (y in 19..21) return "Pasillo Principal"
        if (y == 18 || y == 22) return "En la Puerta"
        if (x in 3..7 && (y in 8..17 || y in 23..31)) return "Vestíbulo (Pasillo Vertical)"

        // 2. ZONA SUPERIOR (Arriba del pasillo, Y entre 8 y 17)
        if (y in 8..17) {
            return when {
                x in 9..12 -> "Aula 2"
                x in 14..24 -> "Aula 3"
                x in 26..30 -> "Aula 4"
                x > 31 -> "Aula 5"
                else -> "Muro"
            }
        }

        // 3. ZONA INFERIOR (Abajo del pasillo, Y entre 23 y 31)
        if (y in 23..31) {
            return when {
                x in 9..14 -> "Aula 7"
                x in 16..21 -> "Aula 8"
                x in 23..29 -> "Aula 9"
                x > 30 -> "Escaleras / Mantenimiento"
                else -> "Muro"
            }
        }

        return "Desconocido (X=$x, Y=$y)"
    }

    // 🟢 VALIDACIÓN DE COLISIONES ACTIVA
    private fun isValidPosition(position: Pair<Int, Int>): Boolean {
        val (x, y) = position
        if (x < 0 || x >= 40 || y < 0 || y >= 40) return false
        return !collisionMap[x][y]
    }

    private fun initializeManagers() {
        val onlineServerManager = OnlineServerManager.getInstance(this).apply {
            setListener(this@Edificio3Activity)
        }
        serverConnectionManager = ServerConnectionManager(this, onlineServerManager)

        movementManager = MovementManager(mapView) { newPosition ->
            if (isValidPosition(newPosition)) {
                playerPosition = newPosition
                updatePlayerPosition(newPosition)
            } else {
                movementManager.setPosition(playerPosition) // Previene que el personaje atraviese paredes
            }
        }

        mapView.setMapTransitionListener(this)
        mapView.playerManager.localPlayerId = playerName
        updatePlayerPosition(playerPosition)
    }

    private fun setupButtonListeners() {
        btnNorth.setOnTouchListener { _, event -> handleMovement(event, 0, -1); true }
        btnSouth.setOnTouchListener { _, event -> handleMovement(event, 0, 1); true }
        btnEast.setOnTouchListener { _, event -> handleMovement(event, 1, 0); true }
        btnWest.setOnTouchListener { _, event -> handleMovement(event, -1, 0); true }

        buttonA.setOnClickListener {
            if (canExitBuilding) {
                returnToEsime()
            } else {
                val room = getRoomName(playerPosition)

                when (room) {
                    "Aula 2" -> {
                        if (energiaRestaurada) {
                            Toast.makeText(this, "⚡ La energía ya está funcionando perfectamente.", Toast.LENGTH_SHORT).show()
                        } else if (tieneFusible) {
                            // Si ya trajo el fusible, AHORA SÍ abrimos el minijuego
                            val intent = Intent(this, MinijuegoEnergiaActivity::class.java)
                            // Le pasamos el dato de si conoce la contraseña o no
                            intent.putExtra("CONOCE_CONTRASENA", tieneContrasena)
                            startActivity(intent)
                        } else {
                            // No tiene el fusible, le damos la pista
                            Toast.makeText(this, "🔌 Panel sin energía. Faltan los fusibles... Quizá haya refacciones en el Aula 9.", Toast.LENGTH_LONG).show()
                        }
                    }
                    "Aula 9" -> {
                        if (!tieneFusible) {
                            tieneFusible = true
                            Toast.makeText(this, "🎒 ¡Encontraste un Fusible de 50A en una caja de herramientas!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Solo hay cables viejos aquí.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Aula 7" -> {
                        if (!tieneContrasena) {
                            tieneContrasena = true
                            Toast.makeText(this, "📝 Lees en el pizarrón: 'No olvidar, clave del panel A2: 1936'", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "📝 Pizarrón: 'Clave del panel A2: 1936'", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Aula 4", "Aula 3", "Aula 5", "Aula 8" -> {
                        Toast.makeText(this, "Estás en $room. Todo se ve normal por aquí.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val (x, y) = playerPosition
                        Toast.makeText(this, "📍 $room (X=$x, Y=$y)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        btnBack.setOnClickListener { returnToEsime() }
    }

    private fun handleMovement(event: MotionEvent, deltaX: Int, deltaY: Int) {
        if (::movementManager.isInitialized) movementManager.handleMovement(event, deltaX, deltaY)
    }

    private fun updatePlayerPosition(position: Pair<Int, Int>) {
        runOnUiThread {
            mapView.updateLocalPlayerPosition(position, forceCenter = true)
            if (::serverConnectionManager.isInitialized) {
                serverConnectionManager.sendUpdateMessage(playerName, position, "edificio3")
            }
            checkPositionForExit(position)
        }
    }

    private fun checkPositionForExit(position: Pair<Int, Int>) {
        // Habilitar salida en los extremos del pasillo
        canExitBuilding = (position.first <= 4 || position.first >= 36) && position.second in 19..21
        if (canExitBuilding) {
            runOnUiThread {
                Toast.makeText(this, "Presiona A para salir del edificio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun returnToEsime() {
        val intent = Intent(this, Esime::class.java).apply {
            putExtra("PLAYER_NAME", playerName)
            putExtra("IS_SERVER", isServer)
            putExtra("INITIAL_POSITION", Pair(8, 17))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (::mapView.isInitialized) mapView.playerManager.cleanup()
        startActivity(intent)
        finish()
    }

    private fun initializeViews() {
        btnNorth = findViewById(R.id.btnNorth)
        btnSouth = findViewById(R.id.btnSouth)
        btnEast = findViewById(R.id.btnEast)
        btnWest = findViewById(R.id.btnWest)
        buttonA = findViewById(R.id.buttonA)
        btnBack = findViewById(R.id.btnBack)
        btnBack.text = "Volver a ESIME"
    }

    override fun onMessageReceived(message: String) {
        runOnUiThread {
            try {
                val json = JSONObject(message)
                if (json.getString("type") in listOf("positions", "update")) {
                    val id = json.getString("id")
                    if (id != playerName) {
                        mapView.updateRemotePlayerPosition(id, Pair(json.getInt("x"), json.getInt("y")), json.getString("map"))
                    }
                }
                mapView.invalidate()
            } catch (e: Exception) {}
        }
    }

    override fun onResume() { super.onResume(); if (::movementManager.isInitialized) movementManager.setPosition(playerPosition) }
    override fun onPause() { super.onPause(); if (::movementManager.isInitialized) movementManager.stopMovement() }
    override fun onDestroy() { super.onDestroy(); if (::mapView.isInitialized) mapView.playerManager.cleanup() }
    override fun onMapTransitionRequested(targetMap: String, pos: Pair<Int, Int>) { if (targetMap == MapMatrixProvider.MAP_ESIME) returnToEsime() }
}