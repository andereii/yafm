package com.anderei.yafm.ui.cleaner

import androidx.annotation.StringRes
import com.anderei.yafm.R

enum class CleanerCategoryType(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    TRASH(R.string.feature_recycle_bin, R.string.cleaner_trash_desc),
    EMPTY(R.string.cleaner_empty_title, R.string.cleaner_empty_desc),
    INSTALLERS(R.string.cleaner_installers_title, R.string.cleaner_installers_desc),
    ADS(R.string.cleaner_ads_title, R.string.cleaner_ads_desc),
    DOWNLOADS(R.string.cleaner_downloads_title, R.string.cleaner_downloads_desc),
}

data class CleanerItem(
    val path: String,
    val displayPath: String = path,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val type: CleanerCategoryType,
)

data class CleanerCategory(
    val type: CleanerCategoryType,
    val items: List<CleanerItem> = emptyList(),
    val count: Int = items.size,
    val totalSize: Long = items.sumOf { it.size },
) {
    companion object {
        fun of(type: CleanerCategoryType, items: List<CleanerItem>): CleanerCategory =
            CleanerCategory(type, items, items.size, items.sumOf { it.size })

        fun trash(count: Int, size: Long): CleanerCategory =
            CleanerCategory(CleanerCategoryType.TRASH, emptyList(), count, size)
    }
}

data class CleanerUiState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val categories: List<CleanerCategory> = emptyList(),
    val expanded: Set<CleanerCategoryType> = emptySet(),
    val selection: Set<String> = emptySet(),
    val trashSelected: Boolean = false,
    val error: String? = null,
) {
    val trashCategory: CleanerCategory?
        get() = categories.firstOrNull { it.type == CleanerCategoryType.TRASH }

    val selectedItems: List<CleanerItem>
        get() = categories
            .asSequence()
            .filter { it.type != CleanerCategoryType.TRASH }
            .flatMap { it.items.asSequence() }
            .filter { it.path in selection }
            .distinctBy { it.path }
            .toList()

    val selectedCount: Int
        get() = selectedItems.size + if (trashSelected) trashCategory?.count ?: 0 else 0

    val selectedSize: Long
        get() = selectedItems.sumOf { it.size } +
            if (trashSelected) trashCategory?.totalSize ?: 0L else 0L

    val totalCount: Int
        get() = categories.sumOf { it.count }

    fun isCategoryFullySelected(category: CleanerCategory): Boolean =
        when (category.type) {
            CleanerCategoryType.TRASH -> trashSelected && category.count > 0
            else -> category.items.isNotEmpty() && category.items.all { it.path in selection }
        }

    fun isCategoryPartiallySelected(category: CleanerCategory): Boolean =
        when (category.type) {
            CleanerCategoryType.TRASH -> false
            else -> category.items.any { it.path in selection } &&
                !category.items.all { it.path in selection }
        }
}
