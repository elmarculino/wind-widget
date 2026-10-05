package com.windwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks the place for a wind + tide widget, when it is added or reconfigured. New places are checked
 * against Windguru and tabuasdemare before they are saved.
 */
class WindTideConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var locations: WindTideLocations
    private val scope = MainScope()

    private lateinit var locationGroup: RadioGroup
    private lateinit var deleteHint: TextView
    private lateinit var nameInput: EditText
    private lateinit var stationInput: EditText
    private lateinit var tideInput: EditText
    private lateinit var addStatus: TextView
    private lateinit var addButton: Button

    private var shown: List<WindTideLocation> = emptyList()
    private var selectedId: String = WindTideLocation.PONTA_VERDE.id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case user backs out
        setResult(Activity.RESULT_CANCELED)

        setContentView(R.layout.activity_configure_tide)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        locations = WindTideLocations(this)
        // Reconfiguring keeps the widget's current place selected
        selectedId = locations.forWidget(appWidgetId).id

        locationGroup = findViewById(R.id.locationGroup)
        deleteHint = findViewById(R.id.deleteHint)
        nameInput = findViewById(R.id.nameInput)
        stationInput = findViewById(R.id.stationInput)
        tideInput = findViewById(R.id.tideInput)
        addStatus = findViewById(R.id.addStatus)
        addButton = findViewById(R.id.addLocationButton)

        addButton.setOnClickListener { addLocation() }
        findViewById<Button>(R.id.confirmButton).setOnClickListener { confirm() }
        showLocations()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showLocations() {
        shown = locations.all()
        // The widget's place may have been deleted from the list; keep it selectable
        locations.forWidget(appWidgetId).takeIf { current -> shown.none { it.id == current.id } }?.let {
            shown = shown + it
        }
        if (shown.none { it.id == selectedId }) selectedId = WindTideLocation.PONTA_VERDE.id

        locationGroup.removeAllViews()
        shown.forEachIndexed { index, location ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = location.name
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                buttonTintList = ColorStateList.valueOf(0xFFA4C9FF.toInt())
                setPadding(paddingLeft, 24, paddingRight, 24)
                tag = index
                if (location.id != WindTideLocation.PONTA_VERDE.id) {
                    setOnLongClickListener { confirmDelete(location); true }
                }
            }
            locationGroup.addView(button)
            if (location.id == selectedId) locationGroup.check(button.id)
        }
        locationGroup.setOnCheckedChangeListener { group, checkedId ->
            val index = group.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            selectedId = shown[index].id
        }
        deleteHint.visibility = if (shown.size > 1) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(location: WindTideLocation) {
        AlertDialog.Builder(this)
            .setTitle("Remover local")
            .setMessage("Remover \"${location.name}\" da lista? Widgets que já mostram este local continuam funcionando.")
            .setPositiveButton("Remover") { _, _ ->
                locations.delete(location.id)
                showLocations()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addLocation() {
        val name = nameInput.text.toString().trim()
        val stationId = WindTideLocation.parseStationId(stationInput.text.toString())
        val slug = WindTideLocation.parseTideSlug(tideInput.text.toString())
        when {
            name.isEmpty() -> return showStatus("Dê um nome ao local")
            stationId == null -> return showStatus("ID da estação Windguru inválido")
            slug == null -> return showStatus("Página de maré inválida")
        }

        addButton.isEnabled = false
        showStatus("Verificando estação e maré…")
        val prefs = WindTideLocations.prefs(this)
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                val wind = async { runCatching { WindguruFetcher(prefs).fetchCurrent(stationId!!, name) } }
                val tide = async { runCatching { TideFetcher(prefs).fetchTable(slug!!) } }
                when {
                    wind.await().isFailure -> "Windguru não respondeu para a estação $stationId"
                    tide.await().isFailure -> "Não achei a tábua de maré \"$slug\" no tabuasdemare.com.br"
                    else -> null
                }
            }
            addButton.isEnabled = true
            if (problem != null) {
                showStatus(problem)
                return@launch
            }
            val added = locations.add(name, stationId!!, slug!!)
            selectedId = added.id
            nameInput.text?.clear()
            stationInput.text?.clear()
            tideInput.text?.clear()
            addStatus.visibility = View.GONE
            showLocations()
        }
    }

    private fun showStatus(text: String) {
        addStatus.text = text
        addStatus.visibility = View.VISIBLE
    }

    private fun confirm() {
        val location = shown.firstOrNull { it.id == selectedId } ?: WindTideLocation.PONTA_VERDE
        locations.setForWidget(appWidgetId, location)

        // Fetch fresh data for the chosen place (bypass any cached reading)
        WindUpdateScheduler.enqueueUpdate(this, intArrayOf(appWidgetId), forceRefresh = true)
        WindUpdateScheduler.scheduleUpdates(this)

        val resultIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
