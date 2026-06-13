package com.asociacionciguena.app.presentation.screens.admin.excursions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

private val DEFAULT_MAP_POSITION = LatLng(40.4168, -3.7038)

@Composable
fun LocationPickerDialog(
    initialLocation: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirm: (location: String, latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val initialPosition = remember(initialLatitude, initialLongitude) {
        if (initialLatitude != null && initialLongitude != null) {
            LatLng(initialLatitude, initialLongitude)
        } else {
            DEFAULT_MAP_POSITION
        }
    }
    var selectedPosition by remember(initialPosition) {
        mutableStateOf<LatLng?>(null)
    }
    var selectedLocation by remember(initialLocation) {
        mutableStateOf(initialLocation)
    }
    var query by remember(initialLocation) {
        mutableStateOf(initialLocation)
    }
    var predictions by remember {
        mutableStateOf<List<AutocompletePrediction>>(emptyList())
    }
    var sessionToken by remember {
        mutableStateOf(AutocompleteSessionToken.newInstance())
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialPosition,
            if (initialLatitude != null && initialLongitude != null) 15f else 5.5f
        )
    }

    LaunchedEffect(query, selectedLocation) {
        parseCoordinates(query)?.let { coordinates ->
            predictions = emptyList()
            searchError = null
            isSearching = false
            selectedLocation = query.trim()
            selectedPosition = coordinates
            return@LaunchedEffect
        }

        if (query.length < 3 || query == selectedLocation) {
            predictions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        delay(350)
        isSearching = true
        searchError = null
        try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setCountries("ES")
                .setRegionCode("ES")
                .setSessionToken(sessionToken)
                .build()
            predictions = placesClient.findAutocompletePredictions(request)
                .await()
                .autocompletePredictions
        } catch (_: Exception) {
            predictions = emptyList()
            searchError = "No se pudieron cargar sugerencias"
        } finally {
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar ubicación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it != selectedLocation) {
                            selectedLocation = ""
                        }
                    },
                    label = { Text("Buscar lugar o dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator()
                        }
                    }
                )

                if (predictions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        items(
                            items = predictions,
                            key = { it.placeId }
                        ) { prediction ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val request = FetchPlaceRequest.builder(
                                            prediction.placeId,
                                            listOf(
                                                Place.Field.DISPLAY_NAME,
                                                Place.Field.FORMATTED_ADDRESS,
                                                Place.Field.LOCATION
                                            )
                                        )
                                            .setSessionToken(sessionToken)
                                            .build()
                                        placesClient.fetchPlace(request)
                                            .addOnSuccessListener { response ->
                                                val place = response.place
                                                val position = place.location
                                                if (position != null) {
                                                    selectedPosition = position
                                                    selectedLocation =
                                                        place.displayName
                                                            ?: place.formattedAddress
                                                            ?: prediction.getFullText(null)
                                                                .toString()
                                                    query = selectedLocation
                                                    predictions = emptyList()
                                                    sessionToken =
                                                        AutocompleteSessionToken.newInstance()
                                                }
                                            }
                                            .addOnFailureListener {
                                                searchError =
                                                    "No se pudo obtener la ubicación"
                                            }
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(prediction.getPrimaryText(null).toString())
                                Text(prediction.getSecondaryText(null).toString())
                            }
                            HorizontalDivider()
                        }
                    }
                }

                searchError?.let { error ->
                    Text(error)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.matchParentSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { position ->
                            val coordinatesText = position.toCoordinatesText()
                            selectedPosition = position
                            selectedLocation = coordinatesText
                            query = coordinatesText
                            predictions = emptyList()
                            searchError = null
                        }
                    ) {
                        val markerPosition = selectedPosition
                            ?: if (initialLatitude != null && initialLongitude != null) {
                                initialPosition
                            } else {
                                null
                            }
                        markerPosition?.let { position ->
                            Marker(
                                state = remember(position) {
                                    MarkerState(position)
                                },
                                title = selectedLocation.ifBlank {
                                    "Ubicación seleccionada"
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val position = selectedPosition ?: initialPosition
                    onConfirm(
                        selectedLocation.ifBlank { query },
                        position.latitude,
                        position.longitude
                    )
                },
                enabled = (selectedPosition != null ||
                    (initialLatitude != null && initialLongitude != null)) &&
                    selectedLocation.ifBlank { query }.isNotBlank()
            ) {
                Text("Usar esta ubicación")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    LaunchedEffect(selectedPosition) {
        selectedPosition?.let { position ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(position, 16f)
            )
        }
    }
}

private fun parseCoordinates(value: String): LatLng? {
    val match = COORDINATES_REGEX.matchEntire(value.trim()) ?: return null
    val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
    val longitude = match.groupValues[2].toDoubleOrNull() ?: return null

    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }

    return LatLng(latitude, longitude)
}

private fun LatLng.toCoordinatesText(): String =
    "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)

private val COORDINATES_REGEX = Regex(
    """^([+-]?(?:\d+(?:\.\d+)?|\.\d+))\s*(?:,\s*|\s+)([+-]?(?:\d+(?:\.\d+)?|\.\d+))$"""
)
