package app.aurora.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CoreModelEnginesTest {

    private val pageOne = PageId(1L)
    private val spec4x6 = GridSpec(columns = 4, rows = 6, dockSlots = 4)

    @Test
    fun `canFit rejects overlap with 2x2 widget and accepts adjacent free cell`() {
        val widget = WorkspaceItem.Widget(
            id = ItemId(10L),
            pageId = pageOne,
            cellX = 1,
            cellY = 1,
            spanX = 2,
            spanY = 2,
            appWidgetId = AppWidgetId(42),
            provider = ComponentKey("com.clock", "ClockWidget", 0L),
        )
        assertFalse(GridOccupancyEngine.canFit(listOf(widget), 4, 6, cellX = 2, cellY = 2, spanX = 1, spanY = 1))
        assertTrue(GridOccupancyEngine.canFit(listOf(widget), 4, 6, cellX = 3, cellY = 2, spanX = 1, spanY = 1))
    }

    @Test
    fun `resolveDrop creates folder when app dropped onto another desktop app`() {
        val appA = WorkspaceItem.App(
            id = ItemId(1L),
            placement = LeafPlacement.Desktop(pageOne, 0, 0),
            component = ComponentKey("app.one", "MainActivity", 0L),
        )
        val appB = WorkspaceItem.App(
            id = ItemId(2L),
            placement = LeafPlacement.Desktop(pageOne, 1, 0),
            component = ComponentKey("app.two", "MainActivity", 0L),
        )
        val snapshot = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageOne, 0, listOf(appA, appB))),
            dockItems = emptyList(),
            gridSpec = spec4x6,
        )

        val resolution = GridOccupancyEngine.resolveDrop(
            snapshot = snapshot,
            draggedItemId = ItemId(1L),
            target = DropTarget.HoverItem(ItemId(2L)),
            defaultFolderTitle = "Sosial",
        )

        assertEquals(
            DropResolution.CreateFolder(
                draggedAppId = ItemId(1L),
                targetAppId = ItemId(2L),
                placement = ContainerPlacement.Desktop(pageOne, 1, 0),
                defaultTitle = "Sosial",
            ),
            resolution,
        )
    }

    @Test
    fun `resolveDrop merges matching-span widgets into widget stack and rejects mismatched span`() {
        val w1 = WorkspaceItem.Widget(
            id = ItemId(10L),
            pageId = pageOne,
            cellX = 0,
            cellY = 0,
            spanX = 2,
            spanY = 2,
            appWidgetId = AppWidgetId(101),
            provider = ComponentKey("pkg.a", "WidgetA", 0L),
        )
        val w2Same = w1.copy(id = ItemId(11L), cellX = 2, cellY = 0, appWidgetId = AppWidgetId(102))
        val w3Diff = w1.copy(id = ItemId(12L), cellX = 0, cellY = 2, spanX = 4, spanY = 1, appWidgetId = AppWidgetId(103))

        val snapshot = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageOne, 0, listOf(w1, w2Same, w3Diff))),
            dockItems = emptyList(),
            gridSpec = spec4x6,
        )

        assertEquals(
            DropResolution.MergeIntoWidgetStack(ItemId(10L), ItemId(11L)),
            GridOccupancyEngine.resolveDrop(snapshot, ItemId(10L), DropTarget.HoverItem(ItemId(11L))),
        )
        assertTrue(
            GridOccupancyEngine.resolveDrop(snapshot, ItemId(10L), DropTarget.HoverItem(ItemId(12L))) is DropResolution.Reject,
        )
    }

    @Test
    fun `normalizeSnapshot dissolves 1-item folder back to its desktop coordinates and prunes empty trailing page`() {
        val soleChild = WorkspaceItem.App(
            id = ItemId(7L),
            placement = LeafPlacement.InFolder(ItemId(99L), 0),
            component = ComponentKey("com.whatsapp", "Main", 0L),
        )
        val singleFolder = WorkspaceItem.Folder(
            id = ItemId(99L),
            placement = ContainerPlacement.Desktop(pageOne, 2, 3),
            title = "Temp",
            children = listOf(soleChild),
        )
        val emptyPageTwo = WorkspacePage(PageId(2L), 1, emptyList())

        val normalized = GridOccupancyEngine.normalizeSnapshot(
            WorkspaceSnapshot(
                pages = listOf(WorkspacePage(pageOne, 0, listOf(singleFolder)), emptyPageTwo),
                dockItems = emptyList(),
                gridSpec = spec4x6,
            ),
        )

        assertEquals(1, normalized.pages.size)
        assertEquals(
            listOf(soleChild.copy(placement = LeafPlacement.Desktop(pageOne, 2, 3))),
            normalized.pages.first().items,
        )
    }

    @Test
    fun `reflowWorkspace clamps 5-wide widget to 4 columns and spills excess items onto next page`() {
        val wideWidget = WorkspaceItem.Widget(
            id = ItemId(50L),
            pageId = pageOne,
            cellX = 0,
            cellY = 0,
            spanX = 5,
            spanY = 4,
            appWidgetId = AppWidgetId(500),
            provider = ComponentKey("pkg.weather", "Wide", 0L),
        )
        val apps = (1..6).map { idx ->
            WorkspaceItem.App(
                id = ItemId(idx.toLong()),
                placement = LeafPlacement.Desktop(pageOne, (idx - 1) % 5, 4 + (idx - 1) / 5),
                component = ComponentKey("pkg.app$idx", "Main", 0L),
            )
        }
        val sourceSpec = GridSpec(columns = 5, rows = 6, dockSlots = 5)
        val targetSpec = GridSpec(columns = 4, rows = 4, dockSlots = 4)

        val reflowed = GridOccupancyEngine.reflowWorkspace(
            snapshot = WorkspaceSnapshot(
                pages = listOf(WorkspacePage(pageOne, 0, listOf(wideWidget) + apps)),
                dockItems = emptyList(),
                gridSpec = sourceSpec,
            ),
            targetSpec = targetSpec,
            nextPageIdGenerator = { pos -> PageId(100L + pos) },
        )

        assertEquals(2, reflowed.pages.size)
        val placedWidget = reflowed.pages[0].items.single() as WorkspaceItem.Widget
        assertEquals(4, placedWidget.spanX)
        assertEquals(4, placedWidget.spanY)
        assertEquals(6, reflowed.pages[1].items.size)
    }

    @Test
    fun `AppLibraryClassifier enforces 5-tier priority and strips diacritics and leading emoji in sortKey`() {
        assertEquals(
            LibraryGroup.TRAVEL,
            AppLibraryClassifier.classify(
                packageName = "com.whatsapp",
                userOverride = LibraryGroup.TRAVEL,
                systemCategory = AppLibraryClassifier.CATEGORY_SOCIAL,
            ),
        )
        assertEquals(
            LibraryGroup.GAMES,
            AppLibraryClassifier.classify(
                packageName = "unknown.studio.puzzle",
                systemCategory = AppLibraryClassifier.CATEGORY_GAME,
            ),
        )
        assertEquals(
            LibraryGroup.SHOPPING,
            AppLibraryClassifier.classify(packageName = "com.tokopedia.tkpd"),
        )
        assertEquals(
            LibraryGroup.PHOTO_VIDEO,
            AppLibraryClassifier.classify(
                packageName = "org.custom.lens",
                hints = AppClassificationHints(handlesCameraIntent = true),
            ),
        )
        assertEquals("ecole", AppLibraryClassifier.buildSortKey("✨ École", Locale.FRENCH))
        assertEquals("E", AppLibraryClassifier.sectionInitial("✨ École", Locale.FRENCH))
    }

    @Test
    fun `SpotlightSearchEngine matches initials, diacritic-insensitive prefix, and hides hidden apps`() {
        val gm = AppEntry(
            key = ComponentKey("com.google.android.apps.maps", "Maps", 0L),
            label = "Google Maps",
            sortKey = "google maps",
            launchCount = 12,
        )
        val secret = AppEntry(
            key = ComponentKey("com.secret.vault", "Vault", 0L),
            label = "Google Vault",
            sortKey = "google vault",
            isHidden = true,
            launchCount = 99,
        )
        val cafe = AppEntry(
            key = ComponentKey("id.cafe.order", "Main", 0L),
            label = "Café Nusantara",
            sortKey = "cafe nusantara",
        )

        val initialsResult = SpotlightSearchEngine.search("gm", listOf(gm, secret, cafe))
        assertEquals(listOf(gm), initialsResult.matchingApps)

        val diacriticResult = SpotlightSearchEngine.search("cafe", listOf(gm, secret, cafe))
        assertEquals(listOf(cafe), diacriticResult.matchingApps)
    }

    @Test
    fun `SpotlightSearchEngine evaluates arithmetic expressions deterministically`() {
        assertEquals("100", SpotlightSearchEngine.evaluateArithmeticExpression("25 * 4")?.result)
        assertEquals("50", SpotlightSearchEngine.evaluateArithmeticExpression("100 / 2")?.result)
        assertEquals("30", SpotlightSearchEngine.evaluatePercentageExpression("15% of 200")?.result)
        assertEquals("220", SpotlightSearchEngine.evaluatePercentageExpression("200 + 10%")?.result)
        assertEquals("100", SpotlightSearchEngine.evaluateArithmeticExpression("(12 + 8) * 5")?.result)
        assertEquals("256", SpotlightSearchEngine.evaluateArithmeticExpression("2 ^ 8")?.result)
        assertEquals("2000000000000000", SpotlightSearchEngine.evaluateArithmeticExpression("1000000000000000 * 2")?.result)
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("100 / 0"))
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("Chrome"))
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("+62"))
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("-5"))
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("42"))
        assertNull(SpotlightSearchEngine.evaluateArithmeticExpression("((((((((((((((((((((((((((((((((((((((((1+1))))))))))))))))))))))))))))))))))))))))"))
    }

    @Test
    fun `SpotlightSearchEngine evaluates unit conversions deterministically`() {
        val kmToMiles = SpotlightSearchEngine.evaluateUnitConversion("10 km to miles")
        assertNotNull(kmToMiles)
        assertEquals("6.2137 mi", kmToMiles?.result)

        val kmKeMil = SpotlightSearchEngine.evaluateUnitConversion("10 km ke mil")
        assertNotNull(kmKeMil)
        assertEquals("6.2137 mi", kmKeMil?.result)

        val kgToLbs = SpotlightSearchEngine.evaluateUnitConversion("5 kg to lbs")
        assertNotNull(kgToLbs)
        assertEquals("11.0231 lbs", kgToLbs?.result)

        val mmToCm = SpotlightSearchEngine.evaluateUnitConversion("100 mm to cm")
        assertNotNull(mmToCm)
        assertEquals("10 cm", mmToCm?.result)

        val tonToKg = SpotlightSearchEngine.evaluateUnitConversion("2 ton to kg")
        assertNotNull(tonToKg)
        assertEquals("2000 kg", tonToKg?.result)

        val ydToM = SpotlightSearchEngine.evaluateUnitConversion("100 yard as meter")
        assertNotNull(ydToM)
        assertEquals("91.44 m", ydToM?.result)

        val cToF = SpotlightSearchEngine.evaluateUnitConversion("100 celcius ke fahrenheit")
        assertNotNull(cToF)
        assertEquals("212 °F", cToF?.result)

        val cToFShort = SpotlightSearchEngine.evaluateUnitConversion("100c to f")
        assertNotNull(cToFShort)
        assertEquals("212 °F", cToFShort?.result)

        val fToC = SpotlightSearchEngine.evaluateUnitConversion("32 f to c")
        assertNotNull(fToC)
        assertEquals("0 °C", fToC?.result)

        assertNull(SpotlightSearchEngine.evaluateUnitConversion("10 km to lbs"))
    }

    @Test
    fun `SpotlightSearchEngine returns calculation result in SpotlightResults`() {
        val result = SpotlightSearchEngine.search("25 * 4", emptyList())
        assertNotNull(result.calculationResult)
        assertEquals("100", result.calculationResult?.result)

        val nonCalc = SpotlightSearchEngine.search("Maps", emptyList())
        assertNull(nonCalc.calculationResult)
    }

    @Test
    fun `resolveDrop performs swap displacement when dragging 1x1 app onto occupied 1x1 cell`() {
        val app1 = WorkspaceItem.App(
            id = ItemId(1L),
            placement = LeafPlacement.Desktop(pageOne, 0, 0),
            component = ComponentKey("pkg.a", "Main", 0L),
        )
        val app2 = WorkspaceItem.App(
            id = ItemId(2L),
            placement = LeafPlacement.Desktop(pageOne, 1, 0),
            component = ComponentKey("pkg.b", "Main", 0L),
        )
        val snapshot = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageOne, 0, listOf(app1, app2))),
            dockItems = emptyList(),
            gridSpec = spec4x6,
        )

        // Drag app1 onto app2's cell (1, 0)
        val resolution = GridOccupancyEngine.resolveDrop(
            snapshot = snapshot,
            draggedItemId = ItemId(1L),
            target = DropTarget.DesktopCell(pageOne, 1, 0),
        )

        assertEquals(
            DropResolution.SwapItems(
                movedItemId = ItemId(1L),
                movedPageId = pageOne,
                movedCellX = 1,
                movedCellY = 0,
                displacedItemId = ItemId(2L),
                displacedPageId = pageOne,
                displacedCellX = 0,
                displacedCellY = 0,
            ),
            resolution,
        )
    }

    @Test
    fun `packWorkspace compacts gaps across pages in row-major continuous flow`() {
        val app1 = WorkspaceItem.App(
            id = ItemId(1L),
            placement = LeafPlacement.Desktop(pageOne, 0, 0),
            component = ComponentKey("pkg.a", "Main", 0L),
        )
        // Leave gaps at (1,0) and (2,0)
        val app2 = WorkspaceItem.App(
            id = ItemId(2L),
            placement = LeafPlacement.Desktop(pageOne, 3, 0),
            component = ComponentKey("pkg.b", "Main", 0L),
        )
        val pageTwo = PageId(2L)
        val app3OnPageTwo = WorkspaceItem.App(
            id = ItemId(3L),
            placement = LeafPlacement.Desktop(pageTwo, 0, 0),
            component = ComponentKey("pkg.c", "Main", 0L),
        )

        val snapshot = WorkspaceSnapshot(
            pages = listOf(
                WorkspacePage(pageOne, 0, listOf(app1, app2)),
                WorkspacePage(pageTwo, 1, listOf(app3OnPageTwo)),
            ),
            dockItems = emptyList(),
            gridSpec = spec4x6,
        )

        val packed = GridOccupancyEngine.packWorkspace(snapshot)

        // Page 1 now has all 3 apps at (0,0), (1,0), (2,0), and page 2 is pruned
        assertEquals(1, packed.pages.size)
        val pageItems = packed.pages[0].items
        assertEquals(3, pageItems.size)
        val positions = pageItems.map {
            val b = GridOccupancyEngine.itemDesktopBounds(it)!!
            it.id to (b.first to b.second)
        }.toMap()

        assertEquals(0 to 0, positions[ItemId(1L)])
        assertEquals(1 to 0, positions[ItemId(2L)])
        assertEquals(2 to 0, positions[ItemId(3L)])
    }

    @Test
    fun `suggestFolderGroup suggests SOCIAL for matching social apps and GAMES for game apps`() {
        val social = AppLibraryClassifier.suggestFolderGroup(
            firstPackage = "com.whatsapp",
            secondPackage = "org.telegram.messenger",
        )
        assertEquals(LibraryGroup.SOCIAL, social)

        val game = AppLibraryClassifier.suggestFolderGroup(
            firstPackage = "com.supercell.clashofclans",
            secondPackage = "com.roblox.client",
        )
        assertEquals(LibraryGroup.GAMES, game)

        val productivity = AppLibraryClassifier.suggestFolderGroup(
            firstPackage = "com.google.android.gm",
            secondPackage = "com.google.android.keep",
        )
        assertEquals(LibraryGroup.PRODUCTIVITY_FINANCE, productivity)

        val photo = AppLibraryClassifier.suggestFolderGroup(
            firstPackage = "com.google.android.apps.photos",
            secondPackage = "com.google.android.GoogleCamera",
        )
        assertEquals(LibraryGroup.PHOTO_VIDEO, photo)

        val mixed = AppLibraryClassifier.suggestFolderGroup(
            firstPackage = "com.whatsapp",
            secondPackage = "com.roblox.client",
        )
        assertEquals(null, mixed)
    }
}
