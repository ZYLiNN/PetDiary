package com.zylinn.petdiary.ui.pets

import androidx.annotation.DrawableRes
import com.zylinn.petdiary.R
import com.zylinn.petdiary.data.pet.Species

val Species.label: String
    get() = when (this) {
        Species.CAT -> "貓"
        Species.DOG -> "狗"
        Species.RABBIT -> "兔"
        Species.RODENT -> "鼠類"
        Species.BIRD -> "鳥"
        Species.REPTILE -> "爬蟲"
        Species.FISH -> "魚"
        Species.OTHER -> "其他"
    }

@get:DrawableRes
val Species.silhouette: Int
    get() = when (this) {
        Species.CAT -> R.drawable.ic_species_cat
        Species.DOG -> R.drawable.ic_species_dog
        Species.RABBIT -> R.drawable.ic_species_rabbit
        Species.RODENT -> R.drawable.ic_species_rodent
        Species.BIRD -> R.drawable.ic_species_bird
        Species.REPTILE -> R.drawable.ic_species_reptile
        Species.FISH -> R.drawable.ic_species_fish
        Species.OTHER -> R.drawable.ic_species_other
    }
