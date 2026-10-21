package com.zylinn.petdiary.ui.records

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PestControl
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.ui.theme.LocalRecordTypes

/** Icon for each record type; always shown next to the type color (never color alone). */
val RecordType.icon: ImageVector
    get() = when (this) {
        RecordType.FEEDING -> Icons.Outlined.Restaurant
        RecordType.WALK -> DogWalkIcon
        RecordType.PLAY -> Icons.Outlined.Toys
        RecordType.MEDICINE -> Icons.Outlined.Medication
        RecordType.VACCINE -> Icons.Outlined.Vaccines
        RecordType.DEWORMING -> Icons.Outlined.PestControl
        RecordType.WEIGHT -> Icons.Outlined.MonitorWeight
        RecordType.VET -> Icons.Outlined.LocalHospital
        RecordType.GROOMING -> Icons.Outlined.ContentCut
        RecordType.ABNORMAL -> Icons.Outlined.WarningAmber
        RecordType.CUSTOM -> Icons.Outlined.StarBorder
    }

/** Type color for the current light / dark theme. */
val RecordType.color: Color
    @Composable @ReadOnlyComposable
    get() {
        val p = LocalRecordTypes.current
        return when (this) {
            RecordType.FEEDING -> p.feeding
            RecordType.WALK -> p.walk
            RecordType.PLAY -> p.play
            RecordType.MEDICINE -> p.medicine
            RecordType.VACCINE -> p.vaccine
            RecordType.DEWORMING -> p.deworming
            RecordType.WEIGHT -> p.weight
            RecordType.VET -> p.vet
            RecordType.GROOMING -> p.grooming
            RecordType.ABNORMAL -> p.abnormal
            RecordType.CUSTOM -> p.custom
        }
    }
