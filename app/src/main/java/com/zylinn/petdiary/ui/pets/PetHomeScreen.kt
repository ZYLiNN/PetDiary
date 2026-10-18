package com.zylinn.petdiary.ui.pets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.ui.containerViewModel

/** Minimum tile width: 2 columns on a folded phone, 3–4 when unfolded. */
private val TileMinWidth = 168.dp

@Composable
fun PetHomeRoute(onAddPet: () -> Unit, onOpenPet: (String) -> Unit) {
    val vm = containerViewModel { PetHomeViewModel(it.petRepository, it.photoStore, it.clock) }
    val state by vm.state.collectAsStateWithLifecycle()
    PetHomeScreen(state = state, onAddPet = onAddPet, onOpenPet = onOpenPet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetHomeScreen(
    state: PetHomeState,
    onAddPet: () -> Unit,
    onOpenPet: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的毛孩") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val pets = (state as? PetHomeState.Ready)?.pets ?: return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileMinWidth),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("pet_grid"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pets.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "還沒有毛孩喔！點「＋」新增第一隻吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp).testTag("empty_guide"),
                    )
                }
            }
            items(pets, key = { it.id }) { pet ->
                PetTileCard(pet = pet, onClick = { onOpenPet(pet.id) })
            }
            item(key = "add") { AddPetTile(onClick = onAddPet) }
        }
    }
}

@Composable
private fun PetTileCard(pet: PetTile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("pet_tile_${pet.id}"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(pet.species.silhouette),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp)
                    .semantics { contentDescription = "${pet.species.label}剪影" }
                    .testTag("silhouette_${pet.id}"),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
            ) {
                PetAvatar(name = pet.name, seed = pet.id, photo = pet.photo, size = 76.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    text = pet.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("pet_name_${pet.id}"),
                )
                Text(
                    text = pet.ageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AddPetTile(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp).testTag("add_pet_tile"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.size(6.dp))
            Text("新增寵物", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
