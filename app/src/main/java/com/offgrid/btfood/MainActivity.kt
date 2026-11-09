package com.offgrid.btfood

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

class MainActivity : Activity() {

    private var btAdapter: BluetoothAdapter? = null
    private lateinit var deviceListLayout: LinearLayout
    private lateinit var ordersListLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var nameInput: EditText
    private lateinit var landmarkInput: EditText

    private val menu = listOf(
        "Flatbread Pizza ($8)" to 8,
        "Smoked Brisket Sub ($9)" to 9,
        "Hot Coffee Fill ($3)" to 3,
        "Ration + Water Pack ($5)" to 5
    )
    private val cartCounts = IntArray(menu.size)
    private var serverThread: Thread? = null
    private var isRunningServer = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                device?.let { addPeerRow(it.name ?: "BT Node", it.address, rssi, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = manager.adapter

        requestBtPermissions()
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        val rootScroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#121212")) }
        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Header & APK Beam Button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "OFF-GRID BITES (No-GPS BT)"
            textSize = 18f
            setTextColor(Color.parseColor("#4CAF50"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val beamBtn = Button(this).apply {
            text = "Beam APK"
            setOnClickListener { shareApkFile() }
        }
        headerRow.addView(title)
        headerRow.addView(beamBtn)
        mainCol.addView(headerRow)

        // Inputs for Callsign & Landmark (Replaces GPS)
        nameInput = EditText(this).apply {
            hint = "Your Name / Callsign"
            setText("Unit-01")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        landmarkInput = EditText(this).apply {
            hint = "Drop Landmark (e.g. Red Tent, Porch #4)"
            setText("Camp Sector B - Blue Tarp")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        mainCol.addView(nameInput)
        mainCol.addView(landmarkInput)

        // Menu Cart Section
        menu.forEachIndexed { idx, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                text = "${item.first} [x0]"
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val addBtn = Button(this).apply {
                text = "+"
                setOnClickListener {
                    cartCounts[idx]++
                    label.text = "${item.first} [x${cartCounts[idx]}]"
                }
            }
            val subBtn = Button(this).apply {
                text = "-"
                setOnClickListener {
                    if (cartCounts[idx] > 0) cartCounts[idx]--
                    label.text = "${item.first} [x${cartCounts[idx]}]"
                }
            }
            row.addView(label)
            row.addView(subBtn)
            row.addView(addBtn)
            mainCol.addView(row)
        }

        // Control Buttons: Scan, Make Visible, Start Vendor Server
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scanBtn = Button(this).apply {
            text = "Scan Nodes"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { scanBluetoothNodes() }
        }
        val visBtn = Button(this).apply {
            text = "Make Visible"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { makeVisible() }
        }
        val vendorBtn = Button(this).apply {
            text = "Start Vendor Server"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { startVendorServer() }
        }
        actionRow.addView(scanBtn)
        actionRow.addView(visBtn)
        actionRow.addView(vendorBtn)
        mainCol.addView(actionRow)

        statusText = TextView(this).apply {
            text = "Status: Customer Mode (Tap a scanned node below to beam order)"
            setTextColor(Color.YELLOW)
            setPadding(0, 16, 0, 16)
        }
        mainCol.addView(statusText)

        deviceListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainCol.addView(deviceListLayout)

        val incomingHeader = TextView(this).apply {
            text = "--- INCOMING VENDOR ORDERS ---"
            setTextColor(Color.CYAN)
            setPadding(0, 24, 0, 8)
        }
        mainCol.addView(incomingHeader)

        ordersListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainCol.addView(ordersListLayout)

        rootScroll.addView(mainCol)
        setContentView(rootScroll)
    }

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ), 101
            )
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothNodes() {
        deviceListLayout.removeAllViews()
        btAdapter?.bondedDevices?.forEach { d ->
            addPeerRow(d.name ?: "Paired Node", d.address, -60, d)
        }
        if (btAdapter?.isDiscovering == true) btAdapter?.cancelDiscovery()
        btAdapter?.startDiscovery()
        statusText.text = "Scanning nearby Bluetooth signals & RSSI distance..."
    }

    private fun addPeerRow(name: String, addr: String, rssi: Int, device: BluetoothDevice) {
        val zone = when {
            rssi >= -55 -> "Immediate (<2m)"
            rssi >= -72 -> "Near (2-10m)"
            rssi >= -85 -> "Approaching (10-25m)"
            else -> "Far (>25m)"
        }
        val btn = Button(this).apply {
            text = "$name ($addr) • $rssi dBm [$zone]\nTap to Send Order Here"
            setOnClickListener { sendOrderToNode(device) }
        }
        deviceListLayout.addView(btn)
    }

    @SuppressLint("MissingPermission")
    private fun sendOrderToNode(device: BluetoothDevice) {
        val items = mutableListOf<String>()
        var total = 0
        menu.forEachIndexed { idx, pair ->
            if (cartCounts[idx] > 0) {
                items.add("${cartCounts[idx]}x ${pair.first}")
                total += cartCounts[idx] * pair.second
            }
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "Select at least 1 food item!", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject().apply {
            put("user", nameInput.text.toString())
            put("landmark", landmarkInput.text.toString())
            put("items", items.joinToString(", "))
            put("total", total)
        }.toString()

        Thread {
            try {
                btAdapter?.cancelDiscovery()
                val socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()
                socket.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                socket.close()
                runOnUiThread {
                    Toast.makeText(this, "Order beamed via Bluetooth!", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "BT Send Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun startVendorServer() {
        if (isRunningServer) return
        isRunningServer = true
        statusText.text = "Status: VENDOR SERVER ACTIVE (Listening on RFCOMM)"

        serverThread = Thread {
            try {
                val server = btAdapter?.listenUsingInsecureRfcommWithServiceRecord("OffGridBites", APP_UUID)
                while (isRunningServer) {
                    val socket = server?.accept() ?: break
                    val buf = ByteArray(2048)
                    val len = socket.inputStream.read(buf)
                    if (len > 0) {
                        val json = JSONObject(String(buf, 0, len, Charsets.UTF_8))
                        val remoteAddr = socket.remoteDevice?.address ?: "Unknown"
                        runOnUiThread {
                            val card = TextView(this).apply {
                                text = "ORDER from ${json.optString("user")} [$remoteAddr]\n" +
                                       "Drop Zone: ${json.optString("landmark")}\n" +
                                       "Items: ${json.optString("items")} ($${json.optInt("total")})"
                                setTextColor(Color.WHITE)
                                setBackgroundColor(Color.parseColor("#263238"))
                                setPadding(24, 24, 24, 24)
                            }
                            ordersListLayout.addView(card, 0)
                        }
                    }
                    socket.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.apply { start() }
    }

    @SuppressLint("MissingPermission")
    private fun makeVisible() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(intent)
    }

    private fun shareApkFile() {
        try {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val srcApk = File(applicationInfo.sourceDir)
            // Copy to external cache with .apk extension (or .zip if blocked by legacy OBEX)
            val dstApk = File(externalCacheDir ?: cacheDir, "OffGridBites.apk")
            srcApk.copyTo(dstApk, overwrite = true)
            dstApk.setReadable(true, false)

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(dstApk))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Beam APK via Bluetooth"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunningServer = false
        unregisterReceiver(receiver)
    }
}
