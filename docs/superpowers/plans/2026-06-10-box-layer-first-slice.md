# Box Layer First Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real Box/BoxLayer data and UI slice so the app can create a component box, generate its layers, show those layers, and print a 10 mm layer label from a selected layer.

**Architecture:** Add explicit Box, BoxLayer, and LayerMaterial tables beside the existing warehouse inventory tables. Keep the legacy Inventory route available for current stock flows, but add a new Boxes route as the product-forward entry point. Reuse the existing `BoxLayerLabelBitmap` and `PrinterManager` path; do not change P0/Q5 protocol code in this slice.

**Tech Stack:** Android Kotlin, Jetpack Compose, Room + KSP schema export, DataStore preferences, existing app-level `AppContainer`, existing printer abstraction.

---

## Current State

Current branch:

```text
codex/add-p0-yinlifang-printer
```

Relevant commits already present:

```text
b9356c9 printer: add verified P0 smoke print workflow
15fec8f printer: add editable 10mm box label preview
```

Important existing docs:

- `docs/superpowers/specs/2026-06-10-box-layer-material-label-design.md`
- `docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md`

Current local untracked files are validation artifacts and should stay uncommitted unless the user explicitly asks:

- `asset/BOM.xlsx`
- `tmp/*`

Use this build command for verification:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home-test'
.\gradlew.bat --no-daemon --init-script tmp\gradle-mirror-init.gradle.kts :app:compileDebugKotlin --no-configuration-cache
```

Do not modify global Scoop, Java, Gradle, or Android SDK environment variables.

## Execution Strategy

Use the main agent as coordinator and final integrator. Use subagents for bounded work and reviews.

Suggested skills for the next session:

- `superpowers:using-superpowers`
- `superpowers:subagent-driven-development`
- `superpowers:dispatching-parallel-agents`
- `superpowers:verification-before-completion`
- `superpowers:receiving-code-review` if a reviewer reports issues

Suggested subagents:

1. **Schema worker**: owns Room entities, DAO, database version 6 migration, and schema export.
2. **Repository worker**: owns domain models, repository interface, repository implementation, and `AppContainer` wiring.
3. **UI worker**: owns `feature/boxes`, route wiring, strings, and label print integration.
4. **Spec reviewer**: read-only review against the two spec docs and this plan.
5. **Code quality reviewer**: read-only review of final diff before commit.

Avoid parallel edits to the same files. The main agent should own final edits to:

- `app/src/main/java/com/example/lcsc_android_erp/core/database/AppDatabase.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/DatabaseMigrations.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`
- `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Project instruction says not to add new tests unless explicitly requested. For this slice, use focused compile verification and Room schema export as the main checks.

## Scope

Build now:

- Create boxes with code, optional name, and layer count.
- Auto-generate layers `L01` through `LNN`.
- Show boxes and their layers.
- Show whether a layer is empty or has a bound component if data exists.
- Print a 10 mm label for a selected layer:
  - Empty layer: position-only label, for example `BOX01-L03`.
  - Bound layer: position plus LCSC part, for example `BOX01-L03` and `C17710`.
- Keep legacy inventory/search/inbound flows working.

Do not build in this slice:

- BOM rewrite.
- NFC payload rewrite.
- Component-to-layer assignment UI from BOM.
- Legacy `storage_location` migration into boxes.
- Backup/export support for the new box tables.
- Physical printer protocol changes.
- QR in the 10 mm label profile.

## File Map

Create:

- `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/BoxEntity.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/BoxLayerEntity.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/LayerMaterialEntity.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/BoxDao.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/model/BoxSummaryProjection.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/model/BoxLayerProjection.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/model/ComponentBox.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/model/ComponentBoxLayer.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/repository/BoxRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesUiState.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesScreen.kt`

Modify:

- `app/src/main/java/com/example/lcsc_android_erp/core/database/AppDatabase.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/DatabaseMigrations.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`
- `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md`

Generated by Room after compile:

- `app/schemas/com.example.lcsc_android_erp.core.database.AppDatabase/6.json`

## Task 1: Add Room Box Schema

**Files:**

- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/BoxEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/BoxLayerEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/LayerMaterialEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/BoxDao.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/model/BoxSummaryProjection.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/model/BoxLayerProjection.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/database/DatabaseMigrations.kt`

- [ ] **Step 1: Create `BoxEntity`**

Use table name `component_box` to avoid overloading generic SQL/domain words.

```kotlin
package com.example.lcsc_android_erp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "component_box",
    indices = [Index(value = ["code"], unique = true)]
)
data class BoxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val name: String? = null,
    val layerCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create `BoxLayerEntity`**

```kotlin
package com.example.lcsc_android_erp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "box_layer",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["box_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["box_id"]),
        Index(value = ["box_id", "layer_code"], unique = true)
    ]
)
data class BoxLayerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "box_id")
    val boxId: Long,
    @ColumnInfo(name = "layer_code")
    val layerCode: String,
    val displayName: String? = null,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Create `LayerMaterialEntity`**

This table is included now so the schema supports one component per layer, but the first UI slice does not need full binding UI.

```kotlin
package com.example.lcsc_android_erp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "layer_material",
    foreignKeys = [
        ForeignKey(
            entity = BoxLayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["layer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ComponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["layer_id"], unique = true),
        Index(value = ["component_id"])
    ]
)
data class LayerMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "layer_id")
    val layerId: Long,
    @ColumnInfo(name = "component_id")
    val componentId: Long,
    val quantity: Int = 0,
    val sourceType: String? = null,
    val rawPayload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Create projections**

`BoxSummaryProjection.kt`:

```kotlin
package com.example.lcsc_android_erp.core.database.model

data class BoxSummaryProjection(
    val id: Long,
    val code: String,
    val name: String?,
    val layerCount: Int,
    val occupiedLayerCount: Int
)
```

`BoxLayerProjection.kt`:

```kotlin
package com.example.lcsc_android_erp.core.database.model

data class BoxLayerProjection(
    val id: Long,
    val boxId: Long,
    val boxCode: String,
    val layerCode: String,
    val displayName: String?,
    val sortOrder: Int,
    val componentId: Long?,
    val partNumber: String?,
    val componentName: String?,
    val quantity: Int?
)
```

- [ ] **Step 5: Create `BoxDao`**

```kotlin
package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.entity.LayerMaterialEntity
import com.example.lcsc_android_erp.core.database.model.BoxLayerProjection
import com.example.lcsc_android_erp.core.database.model.BoxSummaryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface BoxDao {
    @Query(
        """
        SELECT
            b.id AS id,
            b.code AS code,
            b.name AS name,
            b.layerCount AS layerCount,
            CAST(COUNT(lm.id) AS INTEGER) AS occupiedLayerCount
        FROM component_box b
        LEFT JOIN box_layer bl ON bl.box_id = b.id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        GROUP BY b.id
        ORDER BY b.code ASC
        """
    )
    fun observeBoxSummaries(): Flow<List<BoxSummaryProjection>>

    @Query("SELECT * FROM component_box WHERE code = :code LIMIT 1")
    suspend fun findBoxByCode(code: String): BoxEntity?

    @Query("SELECT * FROM component_box WHERE id = :boxId LIMIT 1")
    suspend fun findBoxById(boxId: Long): BoxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBox(box: BoxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLayers(layers: List<BoxLayerEntity>)

    @Update
    suspend fun updateBox(box: BoxEntity)

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.partNumber AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE bl.box_id = :boxId
        ORDER BY bl.sortOrder ASC, bl.layer_code ASC
        """
    )
    fun observeLayersForBox(boxId: Long): Flow<List<BoxLayerProjection>>

    @Query("SELECT COUNT(*) FROM box_layer WHERE box_id = :boxId")
    suspend fun countLayersForBox(boxId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceLayerMaterial(layerMaterial: LayerMaterialEntity)
}
```

- [ ] **Step 6: Update `AppDatabase` to version 6**

Add the new entities and DAO:

```kotlin
@Database(
    entities = [
        ComponentEntity::class,
        StorageLocationEntity::class,
        InventoryItemEntity::class,
        InventoryTransactionEntity::class,
        BoxEntity::class,
        BoxLayerEntity::class,
        LayerMaterialEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun componentDao(): ComponentDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun boxDao(): BoxDao
}
```

- [ ] **Step 7: Add migration 5 to 6**

Use SQL that matches the entities exactly.

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS component_box (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                name TEXT,
                layerCount INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_component_box_code " +
                "ON component_box (code)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS box_layer (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                box_id INTEGER NOT NULL,
                layer_code TEXT NOT NULL,
                displayName TEXT,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(box_id) REFERENCES component_box(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_box_layer_box_id ON box_layer (box_id)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_box_layer_box_id_layer_code " +
                "ON box_layer (box_id, layer_code)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS layer_material (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                layer_id INTEGER NOT NULL,
                component_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                sourceType TEXT,
                rawPayload TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(layer_id) REFERENCES box_layer(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(component_id) REFERENCES component_master(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_layer_material_layer_id " +
                "ON layer_material (layer_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_layer_material_component_id " +
                "ON layer_material (component_id)"
        )
    }
}
```

Update `DatabaseMigrations.ALL`:

```kotlin
val ALL = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6
)
```

- [ ] **Step 8: Run compile to generate schema 6**

Run:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home-test'
.\gradlew.bat --no-daemon --init-script tmp\gradle-mirror-init.gradle.kts :app:compileDebugKotlin --no-configuration-cache
```

Expected:

```text
BUILD SUCCESSFUL
```

Check that this file exists:

```text
app/schemas/com.example.lcsc_android_erp.core.database.AppDatabase/6.json
```

- [ ] **Step 9: Commit schema slice**

Stage only schema-related files and commit:

```powershell
git add -- app/src/main/java/com/example/lcsc_android_erp/core/database app/schemas/com.example.lcsc_android_erp.core.database.AppDatabase/6.json
git -c user.name="BrokenClient" -c user.email="brokenclient@foxmail.com" commit -m "boxes: add box layer room schema"
```

## Task 2: Add Box Repository

**Files:**

- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/ComponentBox.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/ComponentBoxLayer.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/repository/BoxRepository.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`

- [ ] **Step 1: Add domain models**

`ComponentBox.kt`:

```kotlin
package com.example.lcsc_android_erp.domain.model

data class ComponentBox(
    val id: Long,
    val code: String,
    val name: String?,
    val layerCount: Int,
    val occupiedLayerCount: Int
)
```

`ComponentBoxLayer.kt`:

```kotlin
package com.example.lcsc_android_erp.domain.model

data class ComponentBoxLayer(
    val id: Long,
    val boxId: Long,
    val boxCode: String,
    val layerCode: String,
    val displayName: String?,
    val sortOrder: Int,
    val componentId: Long?,
    val partNumber: String?,
    val componentName: String?,
    val quantity: Int?
) {
    val positionCode: String = "${boxCode}-${layerCode}"
    val isOccupied: Boolean = partNumber != null
}
```

- [ ] **Step 2: Add repository interface**

```kotlin
package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import kotlinx.coroutines.flow.Flow

interface BoxRepository {
    fun observeBoxes(): Flow<List<ComponentBox>>
    fun observeLayers(boxId: Long): Flow<List<ComponentBoxLayer>>
    suspend fun createBox(code: String, name: String?, layerCount: Int): String?
}
```

`createBox` returns `null` on success or a localized/domain error string on failure.

- [ ] **Step 3: Add repository implementation**

Rules:

- Normalize box code with `trim().uppercase(Locale.ROOT)`.
- Valid code: nonblank, letters/numbers/hyphen/underscore only.
- Valid layer count: `1..99`.
- Generate layer codes with two digits for `1..99`: `L01`, `L02`, `L20`.
- Use `database.withTransaction`.
- If insert returns `-1`, return duplicate error.

Implementation outline:

```kotlin
class BoxRepositoryImpl(
    private val database: RoomDatabase,
    private val boxDao: BoxDao
) : BoxRepository {
    override fun observeBoxes(): Flow<List<ComponentBox>> {
        return boxDao.observeBoxSummaries().map { boxes ->
            boxes.map { projection ->
                ComponentBox(
                    id = projection.id,
                    code = projection.code,
                    name = projection.name,
                    layerCount = projection.layerCount,
                    occupiedLayerCount = projection.occupiedLayerCount
                )
            }
        }
    }

    override fun observeLayers(boxId: Long): Flow<List<ComponentBoxLayer>> {
        return boxDao.observeLayersForBox(boxId).map { layers ->
            layers.map { projection ->
                ComponentBoxLayer(
                    id = projection.id,
                    boxId = projection.boxId,
                    boxCode = projection.boxCode,
                    layerCode = projection.layerCode,
                    displayName = projection.displayName,
                    sortOrder = projection.sortOrder,
                    componentId = projection.componentId,
                    partNumber = projection.partNumber,
                    componentName = projection.componentName,
                    quantity = projection.quantity
                )
            }
        }
    }

    override suspend fun createBox(code: String, name: String?, layerCount: Int): String? {
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        if (!normalizedCode.matches(Regex("[A-Z0-9_-]+"))) {
            return "invalid_code"
        }
        if (layerCount !in 1..99) {
            return "invalid_layer_count"
        }

        return database.withTransaction {
            val now = System.currentTimeMillis()
            val boxId = boxDao.insertBox(
                BoxEntity(
                    code = normalizedCode,
                    name = name?.trim()?.ifBlank { null },
                    layerCount = layerCount,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (boxId <= 0) {
                return@withTransaction "duplicate_code"
            }
            boxDao.insertLayers(
                (1..layerCount).map { index ->
                    BoxLayerEntity(
                        boxId = boxId,
                        layerCode = "L%02d".format(Locale.ROOT, index),
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            )
            null
        }
    }
}
```

If the implementation uses string error codes, map them to localized UI strings in `BoxesViewModel`.

- [ ] **Step 4: Wire repository in `AppContainer`**

Add:

```kotlin
val boxRepository: BoxRepository = BoxRepositoryImpl(
    database = database,
    boxDao = database.boxDao()
)
```

- [ ] **Step 5: Run compile**

Run the standard compile command. Fix import or Room query issues before moving on.

- [ ] **Step 6: Commit repository slice**

```powershell
git add -- app/src/main/java/com/example/lcsc_android_erp/domain/model app/src/main/java/com/example/lcsc_android_erp/domain/repository/BoxRepository.kt app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt
git -c user.name="BrokenClient" -c user.email="brokenclient@foxmail.com" commit -m "boxes: add box repository"
```

## Task 3: Add Boxes UI and Navigation

**Files:**

- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesUiState.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesViewModel.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesScreen.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add strings**

Chinese:

```xml
<string name="nav_boxes">盒子</string>
<string name="boxes_title">盒子层位</string>
<string name="boxes_create_box">新增盒子</string>
<string name="boxes_code_label">盒子编号</string>
<string name="boxes_code_placeholder">例如 BOX01</string>
<string name="boxes_name_label">盒子名称</string>
<string name="boxes_layer_count_label">层数</string>
<string name="boxes_layer_count_placeholder">例如 20</string>
<string name="boxes_create_confirm">创建盒子</string>
<string name="boxes_empty">还没有盒子。</string>
<string name="boxes_layers_title">%1$s 层位</string>
<string name="boxes_layer_empty">空层位</string>
<string name="boxes_layer_occupied">%1$s · 数量 %2$s</string>
<string name="boxes_print_layer_label">打印层位标签</string>
<string name="boxes_create_error_invalid_code">盒子编号只能包含字母、数字、横线和下划线。</string>
<string name="boxes_create_error_duplicate_code">盒子编号已存在。</string>
<string name="boxes_create_error_invalid_layer_count">层数必须在 1 到 99 之间。</string>
```

English:

```xml
<string name="nav_boxes">Boxes</string>
<string name="boxes_title">Box Layers</string>
<string name="boxes_create_box">Add Box</string>
<string name="boxes_code_label">Box Code</string>
<string name="boxes_code_placeholder">e.g. BOX01</string>
<string name="boxes_name_label">Box Name</string>
<string name="boxes_layer_count_label">Layer Count</string>
<string name="boxes_layer_count_placeholder">e.g. 20</string>
<string name="boxes_create_confirm">Create Box</string>
<string name="boxes_empty">No boxes yet.</string>
<string name="boxes_layers_title">%1$s Layers</string>
<string name="boxes_layer_empty">Empty layer</string>
<string name="boxes_layer_occupied">%1$s · Qty %2$s</string>
<string name="boxes_print_layer_label">Print Layer Label</string>
<string name="boxes_create_error_invalid_code">Box code can only contain letters, numbers, hyphens, and underscores.</string>
<string name="boxes_create_error_duplicate_code">Box code already exists.</string>
<string name="boxes_create_error_invalid_layer_count">Layer count must be between 1 and 99.</string>
```

- [ ] **Step 2: Add UI state**

```kotlin
package com.example.lcsc_android_erp.feature.boxes

import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer

data class BoxesUiState(
    val boxes: List<ComponentBox> = emptyList(),
    val selectedBox: ComponentBox? = null,
    val selectedBoxLayers: List<ComponentBoxLayer> = emptyList(),
    val createError: String? = null
)
```

- [ ] **Step 3: Add ViewModel**

The ViewModel should:

- observe boxes,
- observe selected box layers,
- expose `selectBox`,
- expose `createBox`,
- map repository error codes to string resource text.

Keep it similar to `InventoryViewModel.Factory`.

- [ ] **Step 4: Add `BoxesRoute` and `BoxesScreen`**

`BoxesRoute` dependencies:

- `appContainer.boxRepository`
- `appContainer.userPreferencesRepository`
- `appContainer.printerManagerForType(preferences.printerType)`

UI shape:

- `LazyColumn` with title.
- Create box form at top.
- Box list.
- When a box is selected, show layer list below or in a dialog.
- Each layer row shows:
  - position code `BOX01-L03`,
  - empty or part summary,
  - print label button.

Layer print behavior:

```kotlin
val bitmap = BoxLayerLabelBitmap.create10MmBitmap(
    positionCode = layer.positionCode,
    partNumber = layer.partNumber.orEmpty()
)
printerManager.printBitmap(bitmap) { errorMessage ->
    // show errorMessage or R.string.printer_print_success
}
```

Gate print button with:

```kotlin
printerState.connectionState == PrinterConnectionState.CONNECTED && !printerState.isPrinting
```

Show `R.string.printer_not_connected` when not connected.

- [ ] **Step 5: Wire navigation**

Add `BoxesRoute` import in `LcscApp.kt`.

Add destination:

```kotlin
data object Boxes : Destination("boxes", R.string.nav_boxes, Icons.Outlined.Inventory2)
```

Add composable:

```kotlin
composable(Destination.Boxes.route) {
    BoxesRoute()
}
```

Top-level destinations should become:

```kotlin
private val topLevelDestinations = listOf(
    Destination.Home,
    Destination.Boxes,
    Destination.Inbound,
    Destination.Search,
    Destination.Printer,
    Destination.Settings
)
```

Keep `Destination.Inventory` and its composable in the nav graph for existing search/inbound/NFC deep links, but remove it from `topLevelDestinations` for this slice. Do not delete `InventoryRoute`.

- [ ] **Step 6: Run compile**

Run the standard compile command. Fix missing imports and Compose API issues.

- [ ] **Step 7: Commit UI slice**

```powershell
git add -- app/src/main/java/com/example/lcsc_android_erp/feature/boxes app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git -c user.name="BrokenClient" -c user.email="brokenclient@foxmail.com" commit -m "boxes: add box layer screen"
```

## Task 4: Documentation and Review

**Files:**

- Modify: `docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md`
- Optionally modify: `docs/superpowers/specs/2026-06-10-box-layer-material-label-design.md`

- [ ] **Step 1: Update findings doc**

Add a short section:

```markdown
## Completed Box/Layer First Slice

Implemented:

1. Explicit Room tables for boxes, layers, and layer-material bindings.
2. A Boxes top-level route for creating boxes and generating layers.
3. Layer-level label printing through the existing 10 mm bitmap profile.

Still pending:

- Component assignment UI.
- BOM-to-layer workflow.
- NFC payload upgrade to explicit box/layer fields.
- Legacy storage location migration.
```

- [ ] **Step 2: Run final compile**

Run:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home-test'
.\gradlew.bat --no-daemon --init-script tmp\gradle-mirror-init.gradle.kts :app:compileDebugKotlin --no-configuration-cache
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Run git review commands**

```powershell
git status --short --untracked-files=all
git log -3 --oneline --decorate
git diff --stat HEAD~3..HEAD
```

Confirm:

- `asset/BOM.xlsx` remains untracked.
- `tmp/*` remains untracked.
- No accidental changes to P0/Q5 protocol files unless required by a compile fix.
- No accidental deletion of legacy Inventory route.

- [ ] **Step 4: Dispatch final reviewers**

Use two read-only subagents:

1. Spec reviewer: compare final diff against this plan and the two spec docs.
2. Code quality reviewer: inspect Room migration/schema, repository transaction behavior, Compose state, and label-print gating.

Fix any concrete findings before final commit.

- [ ] **Step 5: Commit docs/review fixes**

If docs changed after the UI commit:

```powershell
git add -- docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md docs/superpowers/specs/2026-06-10-box-layer-material-label-design.md
git -c user.name="BrokenClient" -c user.email="brokenclient@foxmail.com" commit -m "docs: record box layer first slice"
```

## Final Acceptance Criteria

The next session is successful when all of these are true:

- Room database is version 6.
- `app/schemas/com.example.lcsc_android_erp.core.database.AppDatabase/6.json` exists and is committed.
- A user can open the Boxes tab.
- A user can create `BOX01` with `20` layers.
- The app shows layers `L01` through `L20`.
- A user can print a layer label from a layer row.
- Empty layer print sends a position-only label.
- Bound layer data model exists for future component assignment.
- Legacy Inventory route still exists for existing search/inbound/NFC flows.
- `:app:compileDebugKotlin` passes.
- Work is committed in coherent slices.

## Notes for the Next Agent

- Be decisive. This plan intentionally moves the product from warehouse-first toward box-layer-first.
- Do not block on physical printer retesting; the printer path already exists. Gate the UI by connection state and leave physical verification as the next hardware step.
- Do not start BOM or NFC rewrite in this slice.
- Backup/export currently knows only old inventory tables. The new box tables will not be preserved until a later backup migration slice; note that as an explicit follow-up, not an accidental omission.
- Do not add new tests unless the user explicitly asks in the next session.
- Use `rg` for search and `apply_patch` for manual edits.
- Keep file edits scoped. The main risk is accidentally turning this into a rewrite of the existing Inventory screen.
