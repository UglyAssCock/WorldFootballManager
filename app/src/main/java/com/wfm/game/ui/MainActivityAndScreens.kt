package com.wfm.game.ui

import android.content.ContentValues
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wfm.game.data.*
import com.wfm.game.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            val colorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF10B981),
                    secondary = Color(0xFF3B82F6),
                    background = Color(0xFF0B1120),
                    surface = Color(0xFF1E293B),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF1F5F9)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF059669),
                    secondary = Color(0xFF2563EB)
                )
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WfmMainApp(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

enum class MainNavSection(val label: String) {
    HOME("Home"),
    SQUAD("Squad"),
    TACTICS("Tactics"),
    MATCH("Match 2D"),
    TRANSFERS("Transfers"),
    COMPETITIONS("Leagues"),
    SCOUTING("Scouting"),
    FINANCES("Finances"),
    CLUB("Club & Youth"),
    MANAGER("Manager"),
    EDITOR("Editor"),
    SETTINGS("Saves")
}

@Composable
fun WfmMainApp(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WfmDatabase.getInstance(context) }
    val engine = remember { WorldProgressionEngine(db) }

    var isLoading by remember { mutableStateOf(true) }
    var careerStarted by remember { mutableStateOf(false) }
    var currentSection by remember { mutableStateOf(MainNavSection.HOME) }

    // Global Career State
    var seasonYear by remember { mutableIntStateOf(2026) }
    var currentWeek by remember { mutableIntStateOf(1) }
    var userClubId by remember { mutableStateOf("ENG_D1_C1") }
    var managerProfile by remember {
        mutableStateOf(ManagerProfile(name = "Alex Ferguson Jr.", nationality = "ENG", currentClubId = "ENG_D1_C1", nationalTeamId = "ALG"))
    }
    var tactics by remember { mutableStateOf(TacticalConfig()) }
    var shortlistIds by remember { mutableStateOf(setOf<String>()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var statusBanner by remember { mutableStateOf("") }

    // Seed 420+ Divisions on first launch in background thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            db.ensureWorldSeeded()
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Initializing World Football Database...", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Seeding 67 Nations & 420+ Playable Divisions on SQLite", fontSize = 13.sp, color = Color.Gray)
            }
        }
        return
    }

    if (!careerStarted) {
        CareerSetupAndPreGameScreen(
            db = db,
            onStartCareer = { selectedClub, mgrName, mgrNat, challenge ->
                userClubId = selectedClub.id
                managerProfile = ManagerProfile(
                    name = mgrName.ifBlank { "Coach Pro" },
                    nationality = mgrNat,
                    currentClubId = selectedClub.id,
                    nationalTeamId = selectedClub.countryId,
                    activeChallenge = challenge
                )
                careerStarted = true
            },
            onOpenEditorDirect = {
                careerStarted = true
                currentSection = MainNavSection.EDITOR
            }
        )
        return
    }

    val userClub = remember(userClubId, refreshTrigger) {
        db.getClubById(userClubId) ?: db.searchClubs("", 1).first()
    }
    val userSquad = remember(userClubId, refreshTrigger) {
        db.getPlayersByClub(userClub.id)
    }
    val divisionFixtures = remember(userClub.divisionId, currentWeek, refreshTrigger) {
        db.getFixturesForDivision(userClub.divisionId, currentWeek)
    }
    val currentUserFixture = remember(divisionFixtures, userClub.id) {
        divisionFixtures.find { it.homeClubId == userClub.id || it.awayClubId == userClub.id }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${userClub.name} (${userClub.countryId})",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Season $seasonYear/${(seasonYear + 1) % 100} • Week $currentWeek • Bal: €${userClub.balance / 1_000_000}M • Trf: €${userClub.transferBudget / 1_000_000}M",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = {
                            if (currentUserFixture != null && !currentUserFixture.played) {
                                currentSection = MainNavSection.MATCH
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    engine.simulateGlobalWeek(currentWeek, seasonYear, userClub.id, userClub.divisionId)
                                    val allDivFixtures = db.getFixturesForDivision(userClub.divisionId, -1)
                                    val maxRound = allDivFixtures.maxOfOrNull { it.weekNumber } ?: 30
                                    withContext(Dispatchers.Main) {
                                        if (currentWeek >= maxRound) {
                                            val rolloverReport = engine.processEndOfSeasonRollover(seasonYear, userClub.divisionId, userClub.id)
                                            seasonYear++
                                            currentWeek = 1
                                            statusBanner = "New Season $seasonYear! $rolloverReport"
                                        } else {
                                            currentWeek++
                                            statusBanner = "Advanced to Week $currentWeek."
                                        }
                                        refreshTrigger++
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentUserFixture != null && !currentUserFixture.played) "Play Match" else "Next Week",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Secondary Scrollable Pill Bar for All 12 Sections
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MainNavSection.entries.forEach { sec ->
                        val selected = currentSection == sec
                        FilterChip(
                            selected = selected,
                            onClick = { currentSection = sec },
                            label = { Text(sec.label, fontSize = 12.sp) }
                        )
                    }
                }

                if (statusBanner.isNotBlank()) {
                    Text(
                        text = statusBanner,
                        fontSize = 11.sp,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(tonalElevation = 6.dp) {
                val primaryTabs = listOf(
                    MainNavSection.HOME to Icons.Default.Home,
                    MainNavSection.SQUAD to Icons.Default.Groups,
                    MainNavSection.TACTICS to Icons.Default.SportsSoccer,
                    MainNavSection.TRANSFERS to Icons.Default.SwapHoriz,
                    MainNavSection.EDITOR to Icons.Default.Edit
                )
                primaryTabs.forEach { (sec, icon) ->
                    NavigationBarItem(
                        selected = currentSection == sec,
                        onClick = { currentSection = sec },
                        icon = { Icon(icon, contentDescription = sec.label) },
                        label = { Text(sec.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentSection) {
                MainNavSection.HOME -> HomeScreen(
                    db = db,
                    userClub = userClub,
                    currentWeek = currentWeek,
                    seasonYear = seasonYear,
                    nextFixture = currentUserFixture,
                    manager = managerProfile,
                    refreshTrigger = refreshTrigger,
                    onGoToMatch = { currentSection = MainNavSection.MATCH },
                    onOpenSection = { currentSection = it }
                )
                MainNavSection.SQUAD -> SquadAndPlayerProfileScreen(
                    db = db,
                    userClub = userClub,
                    squad = userSquad,
                    onPlayerUpdated = { refreshTrigger++ }
                )
                MainNavSection.TACTICS -> TacticsScreen(
                    squad = userSquad,
                    tactics = tactics,
                    onUpdateTactics = {
                        tactics = it
                        db.saveOrUpdateClub(userClub.copy(formation = it.formation))
                        statusBanner = "Tactics updated: ${it.formation} (${it.mentality})"
                    }
                )
                MainNavSection.MATCH -> Match2DEngineScreen(
                    db = db,
                    engine = engine,
                    userClub = userClub,
                    userSquad = userSquad,
                    userTactics = tactics,
                    fixture = currentUserFixture,
                    seasonYear = seasonYear,
                    currentWeek = currentWeek,
                    onMatchCompleted = { won, drawn ->
                        managerProfile = managerProfile.copy(
                            careerMatches = managerProfile.careerMatches + 1,
                            careerWins = managerProfile.careerWins + (if (won) 1 else 0),
                            careerDraws = managerProfile.careerDraws + (if (drawn) 1 else 0),
                            careerLosses = managerProfile.careerLosses + (if (!won && !drawn) 1 else 0),
                            boardConfidence = (managerProfile.boardConfidence + (if (won) 3 else if (drawn) 0 else -4)).coerceIn(5, 100)
                        )
                        refreshTrigger++
                    }
                )
                MainNavSection.TRANSFERS -> TransfersAndMarketScreen(
                    db = db,
                    engine = engine,
                    userClub = userClub,
                    shortlistIds = shortlistIds,
                    onToggleShortlist = { pid ->
                        shortlistIds = if (pid in shortlistIds) shortlistIds - pid else shortlistIds + pid
                    },
                    onTransferCompleted = { msg ->
                        statusBanner = msg
                        refreshTrigger++
                    }
                )
                MainNavSection.COMPETITIONS -> CompetitionsAndWorldPyramidScreen(
                    db = db,
                    initialDivisionId = userClub.divisionId,
                    refreshTrigger = refreshTrigger
                )
                MainNavSection.SCOUTING -> ScoutingAndShortlistScreen(
                    db = db,
                    userClub = userClub,
                    shortlistIds = shortlistIds,
                    onToggleShortlist = { pid ->
                        shortlistIds = if (pid in shortlistIds) shortlistIds - pid else shortlistIds + pid
                    }
                )
                MainNavSection.FINANCES -> FinancesAndStadiumScreen(
                    db = db,
                    userClub = userClub,
                    squad = userSquad,
                    onClubUpdated = {
                        db.saveOrUpdateClub(it)
                        refreshTrigger++
                        statusBanner = "Club facilities & finances updated."
                    }
                )
                MainNavSection.CLUB -> YouthAcademyAndReservesScreen(
                    db = db,
                    userClub = userClub,
                    squad = userSquad,
                    onSquadUpdated = { msg ->
                        statusBanner = msg
                        refreshTrigger++
                    }
                )
                MainNavSection.MANAGER -> ManagerCareerAndInternationalScreen(
                    db = db,
                    manager = managerProfile,
                    userClub = userClub,
                    onChangeClub = { newClub ->
                        userClubId = newClub.id
                        managerProfile = managerProfile.copy(currentClubId = newClub.id)
                        statusBanner = "Appointed Manager of ${newClub.name}!"
                        refreshTrigger++
                    },
                    onUpdateManager = { managerProfile = it }
                )
                MainNavSection.EDITOR -> ComprehensiveEditorScreen(
                    db = db,
                    userClub = userClub,
                    onDatabaseEdited = { msg ->
                        statusBanner = msg
                        refreshTrigger++
                    }
                )
                MainNavSection.SETTINGS -> SaveLoadAndSettingsScreen(
                    db = db,
                    seasonYear = seasonYear,
                    currentWeek = currentWeek,
                    manager = managerProfile,
                    tactics = tactics,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onLoadSave = { loadedSeason, loadedWeek, loadedClubId, loadedMgr ->
                        seasonYear = loadedSeason
                        currentWeek = loadedWeek
                        userClubId = loadedClubId
                        managerProfile = loadedMgr
                        refreshTrigger++
                        statusBanner = "Career Save Loaded Successfully."
                    }
                )
            }
        }
    }
}

// =========================================================================
// CAREER SETUP, CHALLENGE SELECTION & 400+ DIVISION PICKER
// =========================================================================
@Composable
fun CareerSetupAndPreGameScreen(
    db: WfmDatabase,
    onStartCareer: (ClubEntity, String, String, String) -> Unit,
    onOpenEditorDirect: () -> Unit
) {
    val countries = remember { WorldFootballCatalog.getAllCountries() }
    var selectedCountry by remember { mutableStateOf(countries.first()) }
    val divisions = remember(selectedCountry) { db.getAllDivisions(selectedCountry.id) }
    var selectedDivision by remember(divisions) { mutableStateOf(divisions.first()) }
    val clubs = remember(selectedDivision) { db.getClubsByDivision(selectedDivision.id) }
    var selectedClub by remember(clubs) { mutableStateOf(clubs.firstOrNull()) }

    var managerName by remember { mutableStateOf("Coach Mourad") }
    var selectedChallenge by remember { mutableStateOf("None (Standard Sandbox Career)") }
    val challenges = listOf(
        "None (Standard Sandbox Career)",
        "Road to Glory: Lowest Division to Top Flight",
        "Youth Academy Only: Sign 0 Players, Develop Homegrown Stars",
        "Financial Crisis: Clear Debt & Survive Relegation",
        "Invincibles Challenge: Win the Domestic League Unbeaten"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("WORLD FOOTBALL MANAGER", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("67 Nations • 420+ Playable Divisions • 2D Match Engine • Full In-Game Editor", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenEditorDirect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Pre-Game World Database Editor")
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Manager Identity & Challenge Mode", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = managerName,
                        onValueChange = { managerName = it },
                        label = { Text("Manager Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Select Career Mode / Challenge:", fontSize = 12.sp, color = Color.Gray)
                    challenges.forEach { ch ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectedChallenge = ch }.padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedChallenge == ch, onClick = { selectedChallenge = ch })
                            Text(ch, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Select Country (${countries.size} Nations Across 6 Confederations)", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        countries.forEach { c ->
                            FilterChip(
                                selected = selectedCountry.id == c.id,
                                onClick = { selectedCountry = c },
                                label = { Text("${c.name} (${c.confederation.code})") }
                            )
                        }
                    }

                    Text("3. Select Division Tier (${divisions.size} Divisions in ${selectedCountry.name})", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        divisions.forEach { d ->
                            FilterChip(
                                selected = selectedDivision.id == d.id,
                                onClick = { selectedDivision = d },
                                label = { Text("T${d.tier}: ${d.name}") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("4. Choose Your Club in ${selectedDivision.name}:", fontWeight = FontWeight.Bold)
        }

        items(clubs) { club ->
            val isChosen = selectedClub?.id == club.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isChosen) 2.dp else 0.dp,
                        color = if (isChosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedClub = club }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(club.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Rep: ${club.reputation} • Transfer Budget: €${club.transferBudget / 1_000_000}M • Stadium: ${club.stadiumCapacity} cap",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            if (club.isFictional) "Generated Lower-League Club (Editable)" else "Verified Competition Club",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Button(onClick = { onStartCareer(club, managerName, selectedCountry.id, selectedChallenge) }) {
                        Text("Manage")
                    }
                }
            }
        }
    }
}

// =========================================================================
// HOME DASHBOARD & INBOX SCREEN
// =========================================================================
@Composable
fun HomeScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    currentWeek: Int,
    seasonYear: Int,
    nextFixture: FixtureEntity?,
    manager: ManagerProfile,
    refreshTrigger: Int,
    onGoToMatch: () -> Unit,
    onOpenSection: (MainNavSection) -> Unit
) {
    val standings = remember(userClub.divisionId, refreshTrigger) { db.getStandings(userClub.divisionId) }
    val pos = standings.indexOfFirst { it.clubId == userClub.id } + 1
    val inbox = remember(refreshTrigger) { db.getInboxMessages() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(userClub.primaryColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(userClub.shortName.take(3).uppercase(), fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(userClub.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Division Position: #${if (pos > 0) pos else "-"} of ${standings.size} • Philosophy: ${userClub.philosophy}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Text("Rep ${userClub.reputation}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Board Confidence: ${manager.boardConfidence}%", fontSize = 12.sp)
                        Text("Fan Support: ${manager.fanConfidence}%", fontSize = 12.sp)
                        Text("Win Rate: ${manager.winPercentage}%", fontSize = 12.sp)
                    }
                    if (manager.activeChallenge != "None (Standard Sandbox Career)") {
                        Text("Active Challenge: ${manager.activeChallenge}", fontSize = 11.sp, color = Color(0xFFFBBF24))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NEXT FIXTURE (WEEK $currentWeek)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (nextFixture != null) {
                        Text(
                            "${nextFixture.homeClubName}  vs  ${nextFixture.awayClubName}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (nextFixture.played) "Result: ${nextFixture.homeGoals} - ${nextFixture.awayGoals}"
                            else "Competition: ${nextFixture.competitionName} (Round ${nextFixture.roundNumber})",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        if (!nextFixture.played) {
                            Button(onClick = onGoToMatch, modifier = Modifier.fillMaxWidth()) {
                                Text("Enter 2D Match Engine")
                            }
                        }
                    } else {
                        Text("No scheduled club match this week. Tap 'Next Week' to advance the calendar.", fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenSection(MainNavSection.SQUAD) }, modifier = Modifier.weight(1f)) {
                    Text("Squad (${db.getPlayersByClub(userClub.id).size})", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onOpenSection(MainNavSection.COMPETITIONS) }, modifier = Modifier.weight(1f)) {
                    Text("420+ Leagues", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onOpenSection(MainNavSection.EDITOR) }, modifier = Modifier.weight(1f)) {
                    Text("In-Game Editor", fontSize = 12.sp)
                }
            }
        }

        item {
            Text("INBOX & WORLD FOOTBALL NEWS (${inbox.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(inbox) { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("[${msg.category}] ${msg.title}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("S${msg.seasonYear} W${msg.week}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(msg.body, fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
    }
}

// =========================================================================
// SQUAD MANAGEMENT & 36-ATTRIBUTE PLAYER PROFILE MODAL
// =========================================================================
@Composable
fun SquadAndPlayerProfileScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    squad: List<PlayerEntity>,
    onPlayerUpdated: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var posFilter by remember { mutableStateOf("ALL") }
    var selectedPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

    val filtered = remember(squad, searchQuery, posFilter) {
        squad.filter {
            (searchQuery.isBlank() || it.fullName.contains(searchQuery, ignoreCase = true)) &&
                (posFilter == "ALL" || it.position == posFilter)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Squad Player...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST").forEach { pos ->
                FilterChip(selected = posFilter == pos, onClick = { posFilter = pos }, label = { Text(pos) })
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(filtered) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedPlayer = p }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${p.squadNumber} ${p.fullName} (${p.nationality})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Pos: ${p.position} • Age: ${p.age} • Morale: ${p.morale}% • Fit: ${p.fitness}% • Goals: ${p.seasonGoals}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Wage: €${p.wageWeekly}/wk • Exp: ${p.contractExpiryYear} • Val: €${p.marketValue / 1000}K",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("OVR ${p.currentAbility}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text("POT ${p.potentialAbility}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    selectedPlayer?.let { player ->
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            title = { Text("${player.fullName} (${player.position} • OVR ${player.currentAbility})") },
            text = {
                val a = player.attributes
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nationality: ${player.nationality} • Age: ${player.age} (Born ${player.birthYear}) • Foot: ${player.preferredFoot}", fontSize = 12.sp)
                    Text("Height: ${player.heightCm}cm • Weight: ${player.weightKg}kg • Personality: ${player.personality}", fontSize = 12.sp)
                    Text("Contract: €${player.wageWeekly}/wk until ${player.contractExpiryYear} • Release Clause: €${player.releaseClause / 1000}K", fontSize = 12.sp)
                    Divider()
                    Text("TECHNICAL ATTRIBUTES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Text("Passing ${a.passing} | Finishing ${a.finishing} | Dribbling ${a.dribbling} | Crossing ${a.crossing} | First Touch ${a.firstTouch} | Tackling ${a.tackling} | Marking ${a.marking} | Heading ${a.heading} | Technique ${a.technique} | Set Pieces ${a.setPieces}", fontSize = 11.sp)
                    Text("MENTAL ATTRIBUTES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Text("Decisions ${a.decisions} | Vision ${a.vision} | Composure ${a.composure} | Positioning ${a.positioning} | Teamwork ${a.teamwork} | Work Rate ${a.workRate} | Determination ${a.determination} | Off The Ball ${a.offTheBall} | Leadership ${a.leadership}", fontSize = 11.sp)
                    Text("PHYSICAL & GK ATTRIBUTES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Text("Pace ${a.pace} | Acceleration ${a.acceleration} | Stamina ${a.stamina} | Strength ${a.strength} | Agility ${a.agility} | Jumping ${a.jumping} | GK Reflexes ${a.reflexes} | Handling ${a.handling} | 1v1 ${a.oneOnOnes}", fontSize = 11.sp)
                    Divider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val updated = player.copy(
                                    contractExpiryYear = player.contractExpiryYear + 2,
                                    wageWeekly = (player.wageWeekly * 110) / 100,
                                    morale = 95
                                )
                                db.saveOrUpdatePlayer(updated)
                                selectedPlayer = updated
                                onPlayerUpdated()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Renew +2 Yrs", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val updated = player.copy(isTransferListed = !player.isTransferListed)
                                db.saveOrUpdatePlayer(updated)
                                selectedPlayer = updated
                                onPlayerUpdated()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (player.isTransferListed) "Unlist Transfer" else "Transfer List", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlayer = null }) { Text("Close") }
            }
        )
    }
}

// =========================================================================
// TACTICAL CREATOR SCREEN
// =========================================================================
@Composable
fun TacticsScreen(
    squad: List<PlayerEntity>,
    tactics: TacticalConfig,
    onUpdateTactics: (TacticalConfig) -> Unit
) {
    val formations = listOf("4-3-3", "4-2-3-1", "4-4-2", "3-5-2", "5-3-2")
    val mentalities = listOf("Very Defensive", "Defensive", "Balanced", "Attacking", "Very Attacking")
    val passingStyles = listOf("Short Passing", "Direct Passing", "Possession", "Counterattacking", "Wing Play")
    val pressingStyles = listOf("High Press", "Mid-Block", "Low Block", "Counter-Press")
    val playerInstructions = listOf("Work Ball Into Box", "Shoot On Sight", "Overlap Fullbacks", "Run In Behind", "Hold Up Ball")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("TACTICAL BOARD & TEAM INSTRUCTIONS", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Tactical Familiarity: ${tactics.familiarity}% • Directly controls 2D pitch positioning and match engine behavior.", fontSize = 12.sp, color = Color.Gray)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Formation", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        formations.forEach { f ->
                            FilterChip(selected = tactics.formation == f, onClick = { onUpdateTactics(tactics.copy(formation = f)) }, label = { Text(f) })
                        }
                    }

                    Text("2. Team Mentality", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mentalities.forEach { m ->
                            FilterChip(selected = tactics.mentality == m, onClick = { onUpdateTactics(tactics.copy(mentality = m)) }, label = { Text(m) })
                        }
                    }

                    Text("3. In-Possession Style", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        passingStyles.forEach { p ->
                            FilterChip(selected = tactics.passingStyle == p, onClick = { onUpdateTactics(tactics.copy(passingStyle = p)) }, label = { Text(p) })
                        }
                    }

                    Text("4. Out-of-Possession Pressing", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pressingStyles.forEach { pr ->
                            FilterChip(selected = tactics.pressingStyle == pr, onClick = { onUpdateTactics(tactics.copy(pressingStyle = pr)) }, label = { Text(pr) })
                        }
                    }

                    Text("5. Attacking Instruction", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        playerInstructions.forEach { inst ->
                            FilterChip(selected = tactics.playerInstruction == inst, onClick = { onUpdateTactics(tactics.copy(playerInstruction = inst)) }, label = { Text(inst) })
                        }
                    }
                }
            }
        }

        item {
            val top11 = squad.sortedByDescending { it.currentAbility }.take(11)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Starting XI & Key Roles (Auto-Picked by Role Ability)", fontWeight = FontWeight.Bold)
                    top11.forEachIndexed { idx, p ->
                        Text("${idx + 1}. [${p.position}] ${p.fullName} — OVR ${p.currentAbility} (Fit ${p.fitness}%)", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// REAL-TIME 2D TOP-DOWN MATCH ENGINE SCREEN
// =========================================================================
@Composable
fun Match2DEngineScreen(
    db: WfmDatabase,
    engine: WorldProgressionEngine,
    userClub: ClubEntity,
    userSquad: List<PlayerEntity>,
    userTactics: TacticalConfig,
    fixture: FixtureEntity?,
    seasonYear: Int,
    currentWeek: Int,
    onMatchCompleted: (Boolean, Boolean) -> Unit
) {
    if (fixture == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No active fixture remaining this week. Tap 'Next Week' on the top bar to advance to the next matchday!")
        }
        return
    }

    val homeClub = remember(fixture.homeClubId) { db.getClubById(fixture.homeClubId) ?: userClub }
    val awayClub = remember(fixture.awayClubId) { db.getClubById(fixture.awayClubId) ?: userClub }
    val homeSquad = remember(homeClub.id) { db.getPlayersByClub(homeClub.id) }
    val awaySquad = remember(awayClub.id) { db.getPlayersByClub(awayClub.id) }

    val simulator = remember(fixture.id) {
        Live2DMatchSimulator(
            fixture = fixture,
            homeClub = homeClub,
            awayClub = awayClub,
            homeSquad = homeSquad,
            awaySquad = awaySquad,
            homeTactics = if (homeClub.id == userClub.id) userTactics else TacticalConfig(formation = homeClub.formation),
            awayTactics = if (awayClub.id == userClub.id) userTactics else TacticalConfig(formation = awayClub.formation)
        )
    }

    var isPaused by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableIntStateOf(1) } // 1x, 2x, 4x
    var tickCounter by remember { mutableIntStateOf(0) }
    var resultCommitted by remember(fixture.id) { mutableStateOf(fixture.played) }

    LaunchedEffect(isPaused, speedMultiplier, resultCommitted) {
        while (!isPaused && !simulator.isFullTime && !resultCommitted) {
            delay((380L / speedMultiplier).coerceAtLeast(60L))
            if (!simulator.isHalfTime) {
                simulator.stepSimulationTick()
                tickCounter++
            }
            if (simulator.isFullTime && !resultCommitted) {
                resultCommitted = true
                val scorers = (simulator.homeNodes + simulator.awayNodes)
                    .filter { it.goals > 0 }
                    .flatMap { node -> List(node.goals) { node.player.id } }
                engine.commitMatchResult(
                    fixture = fixture,
                    homeGoals = simulator.homeScore,
                    awayGoals = simulator.awayScore,
                    goalScorerIds = scorers,
                    summary = "${homeClub.name} ${simulator.homeScore} - ${simulator.awayScore} ${awayClub.name}"
                )
                val userIsHome = homeClub.id == userClub.id
                val won = (userIsHome && simulator.homeScore > simulator.awayScore) || (!userIsHome && simulator.awayScore > simulator.homeScore)
                val drawn = simulator.homeScore == simulator.awayScore
                onMatchCompleted(won, drawn)
            }
        }
    }

    // Read tickCounter so Compose recomposes the 2D pitch every simulation step
    val ignoredTick = tickCounter

    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Scoreboard Header
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${fixture.competitionName} • Round ${fixture.roundNumber}", fontSize = 11.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(homeClub.shortName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${simulator.homeScore}  -  ${simulator.awayScore}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(awayClub.shortName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    when {
                        simulator.isFullTime -> "FULL TIME (90+${simulator.addedTime}')"
                        simulator.isHalfTime -> "HALF TIME (45')"
                        else -> "${simulator.matchMinute}' Min (Tick #$ignoredTick)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )
            }
        }

        // 2D Top-Down Football Pitch Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Turf stripes
                val stripeCount = 10
                val stripeW = w / stripeCount
                for (i in 0 until stripeCount) {
                    drawRect(
                        color = if (i % 2 == 0) Color(0xFF15803D) else Color(0xFF166534),
                        topLeft = Offset(i * stripeW, 0f),
                        size = Size(stripeW, h)
                    )
                }

                // Pitch markings (Halfway line, center circle, penalty boxes, goals)
                val lineStroke = Stroke(width = 3f)
                drawLine(Color.White.copy(alpha = 0.8f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 3f)
                drawCircle(Color.White.copy(alpha = 0.8f), radius = h * 0.18f, center = Offset(w / 2f, h / 2f), style = lineStroke)
                drawRect(Color.White.copy(alpha = 0.8f), topLeft = Offset(0f, h * 0.22f), size = Size(w * 0.14f, h * 0.56f), style = lineStroke)
                drawRect(Color.White.copy(alpha = 0.8f), topLeft = Offset(w * 0.86f, h * 0.22f), size = Size(w * 0.14f, h * 0.56f), style = lineStroke)

                // Draw Home Players (Blue/Primary)
                simulator.homeNodes.forEach { node ->
                    if (!node.redCard) {
                        val px = (node.x * w).coerceIn(12f, w - 12f)
                        val py = (node.y * h).coerceIn(12f, h - 12f)
                        drawCircle(color = Color(0xFF3B82F6), radius = 13f, center = Offset(px, py))
                        drawCircle(color = Color.White, radius = 13f, center = Offset(px, py), style = Stroke(width = 2f))
                    }
                }

                // Draw Away Players (Red/Amber)
                simulator.awayNodes.forEach { node ->
                    if (!node.redCard) {
                        val px = (node.x * w).coerceIn(12f, w - 12f)
                        val py = (node.y * h).coerceIn(12f, h - 12f)
                        drawCircle(color = Color(0xFFEF4444), radius = 13f, center = Offset(px, py))
                        drawCircle(color = Color.Yellow, radius = 13f, center = Offset(px, py), style = Stroke(width = 2f))
                    }
                }

                // Draw Football
                val bx = (simulator.ballX * w).coerceIn(8f, w - 8f)
                val by = (simulator.ballY * h).coerceIn(8f, h - 8f)
                drawCircle(color = Color.White, radius = 7f, center = Offset(bx, by))
                drawCircle(color = Color.Black, radius = 7f, center = Offset(bx, by), style = Stroke(width = 2f))
            }
        }

        // Live Commentary Bar
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = simulator.latestCommentary,
                modifier = Modifier.padding(10.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Match Controls (Pause, 1x/2x/4x, Half-Time Resume, Instant Result, Live Tactics, Quick Sub)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (simulator.isHalfTime) {
                Button(onClick = { simulator.resumeSecondHalf(); tickCounter++ }) {
                    Text("Start 2nd Half")
                }
            } else if (!simulator.isFullTime) {
                OutlinedButton(onClick = { isPaused = !isPaused }) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = {
                    speedMultiplier = when (speedMultiplier) { 1 -> 2; 2 -> 4; else -> 1 }
                }) {
                    Text("Speed: ${speedMultiplier}x")
                }
                Button(onClick = {
                    simulator.simulateToFullTimeInstantly()
                    tickCounter++
                }) {
                    Text("Instant Result")
                }
                OutlinedButton(onClick = {
                    simulator.homeTactics = simulator.homeTactics.copy(
                        mentality = if (simulator.homeTactics.mentality == "Attacking") "Very Attacking" else "Attacking"
                    )
                    simulator.latestCommentary = "Tactical Shout: Switched to ${simulator.homeTactics.mentality}!"
                    tickCounter++
                }) {
                    Text("Shout: Attack!")
                }
                OutlinedButton(onClick = {
                    val tired = simulator.homeNodes.drop(1).minByOrNull { it.stamina }
                    val subIn = simulator.benchHome.firstOrNull()
                    if (tired != null && subIn != null) {
                        simulator.makeUserSubstitution(tired.player.id, subIn)
                        tickCounter++
                    }
                }) {
                    Text("Quick Sub (${5 - simulator.homeSubsUsed} left)")
                }
            }
        }

        // Live Match Stats & Event Timeline
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MATCH STATS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Possession: ${simulator.homePossessionPercent}% - ${100 - simulator.homePossessionPercent}%", fontSize = 11.sp)
                    Text("Shots (On Target): ${simulator.homeShots} (${simulator.homeShotsOnTarget}) - ${simulator.awayShots} (${simulator.awayShotsOnTarget})", fontSize = 11.sp)
                    Text("Corners: ${simulator.homeCorners} - ${simulator.awayCorners}", fontSize = 11.sp)
                    Text("Fouls: ${simulator.homeFouls} - ${simulator.awayFouls}", fontSize = 11.sp)
                }
            }
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text("KEY EVENTS", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    items(simulator.events) { ev ->
                        Text("${ev.minute}' [${ev.type}] ${ev.description}", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// TRANSFER MARKET & CONTRACT NEGOTIATION SCREEN
// =========================================================================
@Composable
fun TransfersAndMarketScreen(
    db: WfmDatabase,
    engine: WorldProgressionEngine,
    userClub: ClubEntity,
    shortlistIds: Set<String>,
    onToggleShortlist: (String) -> Unit,
    onTransferCompleted: (String) -> Unit
) {
    var searchName by remember { mutableStateOf("") }
    var posFilter by remember { mutableStateOf("ALL") }
    var listedOnly by remember { mutableStateOf(false) }
    var negotiatingPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

    val results = remember(searchName, posFilter, listedOnly, userClub.transferBudget) {
        db.searchPlayers(
            nameQuery = searchName,
            positionFilter = posFilter,
            transferListedOnly = listedOnly,
            limit = 75
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "GLOBAL TRANSFER MARKET • Budget: €${userClub.transferBudget / 1000}K • Wage Budget: €${userClub.wageBudgetWeekly / 1000}K/wk",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchName,
                onValueChange = { searchName = it },
                label = { Text("Search Player or Nationality (e.g. ALG, ENG, BRA)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilterChip(
                selected = listedOnly,
                onClick = { listedOnly = !listedOnly },
                label = { Text("Listed Only") }
            )
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST").forEach { pos ->
                FilterChip(selected = posFilter == pos, onClick = { posFilter = pos }, label = { Text(pos) })
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(results) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.fullName} (${p.position} • ${p.nationality} • Age ${p.age})", fontWeight = FontWeight.Bold)
                            Text("OVR ${p.currentAbility} (POT ${p.potentialAbility}) • Value: €${p.marketValue / 1000}K • Wage: €${p.wageWeekly}/wk", fontSize = 12.sp, color = Color.Gray)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onToggleShortlist(p.id) }) {
                                Icon(
                                    if (p.id in shortlistIds) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Shortlist",
                                    tint = Color(0xFFFBBF24)
                                )
                            }
                            Button(onClick = { negotiatingPlayer = p }) {
                                Text("Bid", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    negotiatingPlayer?.let { target ->
        var bidAmount by remember(target) { mutableStateOf(target.marketValue.toString()) }
        var wageOffer by remember(target) { mutableStateOf(target.wageWeekly.toString()) }
        var feedback by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { negotiatingPlayer = null },
            title = { Text("Negotiate: ${target.fullName} (OVR ${target.currentAbility})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Market Value: €${target.marketValue / 1000}K • Release Clause: €${target.releaseClause / 1000}K", fontSize = 12.sp)
                    OutlinedTextField(
                        value = bidAmount,
                        onValueChange = { bidAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Transfer Fee Offer (€)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = wageOffer,
                        onValueChange = { wageOffer = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Weekly Salary Offer (€/wk)") },
                        singleLine = true
                    )
                    if (feedback.isNotBlank()) {
                        Text(feedback, color = Color(0xFFFBBF24), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val (ok, msg) = engine.negotiateTransfer(
                            buyerClub = userClub,
                            targetPlayer = target,
                            offeredFee = bidAmount.toLongOrNull() ?: 0L,
                            offeredWeeklyWage = wageOffer.toLongOrNull() ?: 1000L,
                            isLoanOffer = true
                        )
                        feedback = msg
                        if (ok) {
                            onTransferCompleted(msg)
                            negotiatingPlayer = null
                        }
                    }) {
                        Text("Loan Bid")
                    }
                    Button(onClick = {
                        val (ok, msg) = engine.negotiateTransfer(
                            buyerClub = userClub,
                            targetPlayer = target,
                            offeredFee = bidAmount.toLongOrNull() ?: 0L,
                            offeredWeeklyWage = wageOffer.toLongOrNull() ?: 1000L,
                            isLoanOffer = false
                        )
                        feedback = msg
                        if (ok) {
                            onTransferCompleted(msg)
                            negotiatingPlayer = null
                        }
                    }) {
                        Text("Submit Permanent Bid")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { negotiatingPlayer = null }) { Text("Cancel") }
            }
        )
    }
}

// =========================================================================
// 420+ COMPETITIONS, STANDINGS, CUPS & HISTORICAL WINNERS SCREEN
// =========================================================================
@Composable
fun CompetitionsAndWorldPyramidScreen(
    db: WfmDatabase,
    initialDivisionId: String,
    refreshTrigger: Int
) {
    val allDivisions = remember(refreshTrigger) { db.getAllDivisions() }
    val countries = remember { WorldFootballCatalog.getAllCountries() }
    var selectedCountryId by remember { mutableStateOf(initialDivisionId.substringBefore("_")) }
    val countryDivs = remember(selectedCountryId, allDivisions) {
        allDivisions.filter { it.countryId == selectedCountryId }
    }
    var selectedDiv by remember(countryDivs) {
        mutableStateOf(countryDivs.find { it.id == initialDivisionId } ?: countryDivs.first())
    }
    val standings = remember(selectedDiv.id, refreshTrigger) { db.getStandings(selectedDiv.id) }
    val history = remember(refreshTrigger) { db.getHistoryRecords() }
    var viewTab by remember { mutableIntStateOf(0) } // 0 = Standings, 1 = Fixtures, 2 = History & Cups

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "WORLD COMPETITION DATABASE (${allDivisions.size} Playable Divisions Across ${countries.size} Nations)",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            countries.forEach { c ->
                FilterChip(
                    selected = selectedCountryId == c.id,
                    onClick = { selectedCountryId = c.id },
                    label = { Text("${c.name} (${c.id})") }
                )
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            countryDivs.forEach { d ->
                FilterChip(
                    selected = selectedDiv.id == d.id,
                    onClick = { selectedDiv = d },
                    label = { Text("T${d.tier}: ${d.name}") }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewTab == 0, onClick = { viewTab = 0 }, label = { Text("Standings Table") })
            FilterChip(selected = viewTab == 1, onClick = { viewTab = 1 }, label = { Text("Fixtures & Results") })
            FilterChip(selected = viewTab == 2, onClick = { viewTab = 2 }, label = { Text("Historical Champions") })
        }

        when (viewTab) {
            0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("#", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Club", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("P", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("W-D-L", modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("GD", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("PTS", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider()
                    }
                    items(standings.size) { idx ->
                        val row = standings[idx]
                        val rankColor = when {
                            idx < selectedDiv.promotionSpots || (selectedDiv.tier == 1 && idx < 4) -> Color(0xFF10B981)
                            idx >= standings.size - selectedDiv.relegationSpots && selectedDiv.relegationSpots > 0 -> Color(0xFFEF4444)
                            else -> Color.Unspecified
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${idx + 1}", modifier = Modifier.width(28.dp), fontSize = 12.sp, color = rankColor, fontWeight = FontWeight.Bold)
                            Text(row.clubName, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                            Text("${row.played}", modifier = Modifier.width(28.dp), fontSize = 12.sp)
                            Text("${row.won}-${row.drawn}-${row.lost}", modifier = Modifier.width(56.dp), fontSize = 12.sp)
                            Text("${row.goalDifference}", modifier = Modifier.width(34.dp), fontSize = 12.sp)
                            Text("${row.points}", modifier = Modifier.width(36.dp), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        Divider(color = Color.Gray.copy(alpha = 0.2f))
                    }
                }
            }
            1 -> {
                val fixtures = remember(selectedDiv.id, refreshTrigger) { db.getFixturesForDivision(selectedDiv.id, -1) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(fixtures.take(60)) { f ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("W${f.weekNumber}: ${f.homeClubName} vs ${f.awayClubName}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(
                                    if (f.played) "${f.homeGoals} - ${f.awayGoals}" else "Scheduled",
                                    fontWeight = FontWeight.Bold,
                                    color = if (f.played) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    if (history.isEmpty()) {
                        item {
                            Text("Complete your first season to populate the multi-decade Historical Champions & Golden Boot Archive!", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                    items(history) { h ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${h.seasonYear} • ${h.competitionName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Champion: ${h.winnerClubName} • Runner-Up: ${h.runnerUpClubName}", fontSize = 13.sp)
                                Text("Top Scorer: ${h.topScorerName} (${h.topScorerGoals} goals)", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SCOUTING NETWORK, SHORTLIST & PLAYER COMPARISON SCREEN
// =========================================================================
@Composable
fun ScoutingAndShortlistScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    shortlistIds: Set<String>,
    onToggleShortlist: (String) -> Unit
) {
    var regionFilter by remember { mutableStateOf("ALL") }
    var wonderkidsOnly by remember { mutableStateOf(true) }
    val scoutedPlayers = remember(regionFilter, wonderkidsOnly, shortlistIds) {
        db.searchPlayers(
            nameQuery = if (regionFilter == "ALL") "" else regionFilter,
            maxAge = if (wonderkidsOnly) 21 else 40,
            minOverall = 60,
            limit = 60
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "GLOBAL SCOUTING NETWORK (Level ${userClub.scoutingNetworkLevel}/20) • Shortlisted: ${shortlistIds.size}",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = wonderkidsOnly, onClick = { wonderkidsOnly = !wonderkidsOnly }, label = { Text("U-21 Wonderkids") })
            listOf("ALL", "ENG", "ESP", "FRA", "GER", "ALG", "MAR", "BRA", "ARG", "JPN").forEach { nat ->
                FilterChip(selected = regionFilter == nat, onClick = { regionFilter = nat }, label = { Text(nat) })
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(scoutedPlayers) { p ->
                val grade = when {
                    p.potentialAbility >= 88 -> "A+ Generational Talent"
                    p.potentialAbility >= 82 -> "A First-Team Star"
                    p.potentialAbility >= 75 -> "B+ Solid Starter"
                    else -> "C Squad Rotation"
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.fullName} (${p.position} • ${p.nationality} • Age ${p.age})", fontWeight = FontWeight.Bold)
                            Text("Scout Grade: $grade • Est. OVR ${p.currentAbility} / POT ${p.potentialAbility}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Personality: ${p.personality} • Value: €${p.marketValue / 1000}K", fontSize = 11.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { onToggleShortlist(p.id) }) {
                            Icon(
                                if (p.id in shortlistIds) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Shortlist",
                                tint = Color(0xFFFBBF24)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// CLUB FINANCES, STADIUM EXPANSION & FACILITIES UPGRADE SCREEN
// =========================================================================
@Composable
fun FinancesAndStadiumScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    squad: List<PlayerEntity>,
    onClubUpdated: (ClubEntity) -> Unit
) {
    val totalWageBill = squad.sumOf { it.wageWeekly }
    val projectedGatePerMatch = (userClub.stadiumCapacity * userClub.ticketPrice * 80L) / 100L

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FINANCIAL OVERVIEW & FFP SUSTAINABILITY", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text("Club Balance: €${userClub.balance / 1000}K", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Available Transfer Budget: €${userClub.transferBudget / 1000}K", fontSize = 14.sp)
                    Text("Current Squad Wage Bill: €${totalWageBill / 1000}K/wk (Budget: €${userClub.wageBudgetWeekly / 1000}K/wk)", fontSize = 13.sp)
                    Text("Club Debt: €${userClub.debt / 1000}K • Projected Matchday Gate: €${projectedGatePerMatch / 1000}K", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STADIUM & INFRASTRUCTURE INVESTMENTS", fontWeight = FontWeight.Bold)
                    Text("Stadium: ${userClub.stadiumName} (${userClub.stadiumCapacity} capacity • Ticket: €${userClub.ticketPrice})")
                    Text("Training Facilities: ${userClub.trainingFacilityLevel}/20 • Youth Academy: ${userClub.youthFacilityLevel}/20 • Medical: ${userClub.medicalFacilityLevel}/20", fontSize = 12.sp, color = Color.Gray)

                    Button(
                        onClick = {
                            val cost = 2_500_000L
                            if (userClub.balance >= cost) {
                                onClubUpdated(
                                    userClub.copy(
                                        balance = userClub.balance - cost,
                                        stadiumCapacity = userClub.stadiumCapacity + 5000
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Expand Stadium (+5,000 Seats — €2.5M)")
                    }

                    OutlinedButton(
                        onClick = {
                            val cost = 1_500_000L
                            if (userClub.balance >= cost && userClub.trainingFacilityLevel < 20) {
                                onClubUpdated(
                                    userClub.copy(
                                        balance = userClub.balance - cost,
                                        trainingFacilityLevel = minOf(20, userClub.trainingFacilityLevel + 1),
                                        youthFacilityLevel = minOf(20, userClub.youthFacilityLevel + 1)
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade Training & Youth Academy (+1 Level — €1.5M)")
                    }
                }
            }
        }
    }
}

// =========================================================================
// YOUTH ACADEMY, MENTORING & INDIVIDUAL TRAINING SCREEN
// =========================================================================
@Composable
fun YouthAcademyAndReservesScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    squad: List<PlayerEntity>,
    onSquadUpdated: (String) -> Unit
) {
    val youthPlayers = squad.filter { it.isYouthAcademy || it.age <= 20 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${userClub.name} YOUTH ACADEMY & DEVELOPMENT", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text("Academy Level: ${userClub.youthFacilityLevel}/20 • Club Philosophy: ${userClub.philosophy}", fontSize = 12.sp)
                    Button(
                        onClick = {
                            val country = WorldFootballCatalog.getAllCountries().find { it.id == userClub.countryId }
                                ?: WorldFootballCatalog.COUNTRIES.first()
                            db.seedSquadForClub(db.writableDatabase, userClub.id, country, userClub.reputation, kotlin.random.Random.Default)
                            onSquadUpdated("Recruited new Youth Intake cohort!")
                        }
                    ) {
                        Text("Scout & Promote Instant Youth Intake")
                    }
                }
            }
        }

        items(youthPlayers) { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${p.fullName} (Age ${p.age} • ${p.position})", fontWeight = FontWeight.Bold)
                        Text("Current OVR: ${p.currentAbility} • Potential Ceiling: ${p.potentialAbility} • ${p.personality}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (p.currentAbility < p.potentialAbility) {
                                val boosted = p.copy(
                                    currentAbility = minOf(p.potentialAbility, p.currentAbility + 2),
                                    isYouthAcademy = false
                                )
                                db.saveOrUpdatePlayer(boosted)
                                onSquadUpdated("${p.fullName} completed intensive mentoring (+2 OVR)!")
                            }
                        }
                    ) {
                        Text("Train +2", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// MANAGER CAREER, JOB MARKET & INTERNATIONAL NATIONAL TEAM MANAGEMENT
// =========================================================================
@Composable
fun ManagerCareerAndInternationalScreen(
    db: WfmDatabase,
    manager: ManagerProfile,
    userClub: ClubEntity,
    onChangeClub: (ClubEntity) -> Unit,
    onUpdateManager: (ManagerProfile) -> Unit
) {
    val availableClubs = remember { db.searchClubs("", 30) }
    val countries = remember { WorldFootballCatalog.getAllCountries() }
    var intlResultText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MANAGER PROFILE: ${manager.name}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Qualification: ${manager.coachingBadge} • Reputation: ${manager.reputation}/100", fontSize = 13.sp)
                    Text("Record: ${manager.careerMatches} Played (${manager.careerWins}W - ${manager.careerDraws}D - ${manager.careerLosses}L) • Win Rate: ${manager.winPercentage}%", fontSize = 13.sp)
                    Text("Current Club: ${userClub.name} • National Team Dual-Role: ${manager.nationalTeamId.ifBlank { "None" }}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("INTERNATIONAL FOOTBALL & NATIONAL TEAM MANAGEMENT", fontWeight = FontWeight.Bold)
                    Text("Select a National Team to manage alongside your club in World Cup / AFCON / EURO / Copa América qualifiers:", fontSize = 12.sp, color = Color.Gray)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        countries.take(15).forEach { c ->
                            FilterChip(
                                selected = manager.nationalTeamId == c.id,
                                onClick = { onUpdateManager(manager.copy(nationalTeamId = c.id)) },
                                label = { Text("${c.name} (#${c.fifaRanking})") }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val rivalNat = countries.random()
                            val myGoals = (1..4).random()
                            val oppGoals = (0..2).random()
                            intlResultText = "International Match Result: ${manager.nationalTeamId} $myGoals - $oppGoals ${rivalNat.name} (World Cup / Continental Qualifier)!"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Play International Qualifier Match (${manager.nationalTeamId})")
                    }
                    if (intlResultText.isNotBlank()) {
                        Text(intlResultText, fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("MANAGERIAL JOB MARKET (Apply to Switch Clubs)", fontWeight = FontWeight.Bold)
        }

        items(availableClubs.take(12)) { c ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${c.name} (${c.countryId} • ${c.divisionId})", fontWeight = FontWeight.Bold)
                        Text("Rep: ${c.reputation} • Transfer Budget: €${c.transferBudget / 1_000_000}M", fontSize = 12.sp, color = Color.Gray)
                    }
                    OutlinedButton(onClick = { onChangeClub(c) }) {
                        Text("Take Job")
                    }
                }
            }
        }
    }
}

// =========================================================================
// COMPREHENSIVE IN-GAME EDITOR (PLAYERS, CLUBS, DIVISIONS, CUSTOM CLUB, JSON I/O)
// =========================================================================
@Composable
fun ComprehensiveEditorScreen(
    db: WfmDatabase,
    userClub: ClubEntity,
    onDatabaseEdited: (String) -> Unit
) {
    var editorTab by remember { mutableIntStateOf(0) } // 0 = Players, 1 = Clubs, 2 = Competitions/Custom, 3 = JSON Import/Export

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BUILT-IN WORLD FOOTBALL EDITOR", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
        Text("Warning: Editing attributes, budgets, or divisions modifies the live SQLite database immediately.", fontSize = 11.sp, color = Color(0xFFFBBF24))

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = editorTab == 0, onClick = { editorTab = 0 }, label = { Text("Player Editor") })
            FilterChip(selected = editorTab == 1, onClick = { editorTab = 1 }, label = { Text("Club & Finances Editor") })
            FilterChip(selected = editorTab == 2, onClick = { editorTab = 2 }, label = { Text("Create Club / League") })
            FilterChip(selected = editorTab == 3, onClick = { editorTab = 3 }, label = { Text("JSON Import / Export") })
        }

        when (editorTab) {
            0 -> {
                var query by remember { mutableStateOf("") }
                val players = remember(query) { db.searchPlayers(nameQuery = query, limit = 40) }
                var editingPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search Player to Edit...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = {
                        val newP = PlayerEntity(
                            id = "CUSTOM_P_${System.currentTimeMillis()}",
                            fullName = "Custom Superstar",
                            birthYear = 2005,
                            age = 21,
                            nationality = userClub.countryId,
                            secondNationality = "",
                            position = "ST",
                            secondaryPosition = "LW",
                            preferredFoot = "Right",
                            heightCm = 185,
                            weightKg = 79,
                            clubId = userClub.id,
                            squadNumber = 99,
                            wageWeekly = 50_000L,
                            contractExpiryYear = 2030,
                            releaseClause = 150_000_000L,
                            marketValue = 65_000_000L,
                            morale = 99,
                            fitness = 100,
                            sharpness = 99,
                            reputation = 90,
                            personality = "Model Citizen",
                            injuryWeeks = 0,
                            seasonGoals = 0,
                            seasonAssists = 0,
                            seasonApps = 0,
                            careerGoals = 25,
                            intlCaps = 15,
                            intlGoals = 10,
                            currentAbility = 92,
                            potentialAbility = 97,
                            isYouthAcademy = false,
                            isTransferListed = false,
                            isLoanListed = false,
                            isFictional = false,
                            attributes = db.generateAttributesForPosition("ST", 92, kotlin.random.Random.Default)
                        )
                        db.saveOrUpdatePlayer(newP)
                        editingPlayer = newP
                        onDatabaseEdited("Created new custom player at ${userClub.name}!")
                    }) {
                        Text("+ New")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(players) { p ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { editingPlayer = p }) {
                            Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.fullName} (${p.position} • Age ${p.age} • ${p.nationality})", fontWeight = FontWeight.Bold)
                                Text("OVR ${p.currentAbility} / POT ${p.potentialAbility}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                editingPlayer?.let { p ->
                    var eName by remember(p) { mutableStateOf(p.fullName) }
                    var ePos by remember(p) { mutableStateOf(p.position) }
                    var eAge by remember(p) { mutableStateOf(p.age.toString()) }
                    var eNat by remember(p) { mutableStateOf(p.nationality) }
                    var eCa by remember(p) { mutableStateOf(p.currentAbility.toString()) }
                    var ePa by remember(p) { mutableStateOf(p.potentialAbility.toString()) }
                    var eVal by remember(p) { mutableStateOf(p.marketValue.toString()) }
                    var eClubId by remember(p) { mutableStateOf(p.clubId) }

                    AlertDialog(
                        onDismissRequest = { editingPlayer = null },
                        title = { Text("Edit Player: ${p.id}") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text("Full Name") }, singleLine = true)
                                OutlinedTextField(value = ePos, onValueChange = { ePos = it.uppercase() }, label = { Text("Position (GK, CB, LB, RB, DM, CM, AM, LW, RW, ST)") }, singleLine = true)
                                OutlinedTextField(value = eAge, onValueChange = { eAge = it.filter { c -> c.isDigit() } }, label = { Text("Age") }, singleLine = true)
                                OutlinedTextField(value = eNat, onValueChange = { eNat = it.uppercase() }, label = { Text("Nationality Code (e.g. ALG, ENG)") }, singleLine = true)
                                OutlinedTextField(value = eCa, onValueChange = { eCa = it.filter { c -> c.isDigit() } }, label = { Text("Current Ability (1-99)") }, singleLine = true)
                                OutlinedTextField(value = ePa, onValueChange = { ePa = it.filter { c -> c.isDigit() } }, label = { Text("Potential Ability (1-99)") }, singleLine = true)
                                OutlinedTextField(value = eVal, onValueChange = { eVal = it.filter { c -> c.isDigit() } }, label = { Text("Market Value (€)") }, singleLine = true)
                                OutlinedTextField(value = eClubId, onValueChange = { eClubId = it }, label = { Text("Club ID") }, singleLine = true)
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    db.deletePlayer(p.id)
                                    editingPlayer = null
                                    onDatabaseEdited("Deleted player ${p.fullName}.")
                                }) {
                                    Text("Delete")
                                }
                                Button(onClick = {
                                    val newCa = (eCa.toIntOrNull() ?: p.currentAbility).coerceIn(1, 99)
                                    val newPa = (ePa.toIntOrNull() ?: p.potentialAbility).coerceIn(newCa, 99)
                                    val updated = p.copy(
                                        fullName = eName,
                                        position = ePos,
                                        age = (eAge.toIntOrNull() ?: p.age).coerceIn(15, 45),
                                        nationality = eNat,
                                        currentAbility = newCa,
                                        potentialAbility = newPa,
                                        marketValue = eVal.toLongOrNull() ?: p.marketValue,
                                        clubId = eClubId,
                                        attributes = db.generateAttributesForPosition(ePos, newCa, kotlin.random.Random.Default)
                                    )
                                    db.saveOrUpdatePlayer(updated)
                                    editingPlayer = null
                                    onDatabaseEdited("Saved edits for $eName (OVR $newCa).")
                                }) {
                                    Text("Save Player")
                                }
                            }
                        }
                    )
                }
            }
            1 -> {
                var cName by remember(userClub) { mutableStateOf(userClub.name) }
                var cShort by remember(userClub) { mutableStateOf(userClub.shortName) }
                var cRep by remember(userClub) { mutableStateOf(userClub.reputation.toString()) }
                var cBal by remember(userClub) { mutableStateOf(userClub.balance.toString()) }
                var cTrf by remember(userClub) { mutableStateOf(userClub.transferBudget.toString()) }
                var cStad by remember(userClub) { mutableStateOf(userClub.stadiumName) }
                var cCap by remember(userClub) { mutableStateOf(userClub.stadiumCapacity.toString()) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Text("Edit Active Club (${userClub.id})", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = cName, onValueChange = { cName = it }, label = { Text("Club Full Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cShort, onValueChange = { cShort = it }, label = { Text("Short Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cRep, onValueChange = { cRep = it.filter { c -> c.isDigit() } }, label = { Text("Club Reputation (1-100)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cBal, onValueChange = { cBal = it.filter { c -> c.isDigit() } }, label = { Text("Club Balance (€)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cTrf, onValueChange = { cTrf = it.filter { c -> c.isDigit() } }, label = { Text("Transfer Budget (€)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cStad, onValueChange = { cStad = it }, label = { Text("Stadium Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cCap, onValueChange = { cCap = it.filter { c -> c.isDigit() } }, label = { Text("Stadium Capacity") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val updated = userClub.copy(
                                    name = cName,
                                    shortName = cShort,
                                    reputation = (cRep.toIntOrNull() ?: userClub.reputation).coerceIn(1, 100),
                                    balance = cBal.toLongOrNull() ?: userClub.balance,
                                    transferBudget = cTrf.toLongOrNull() ?: userClub.transferBudget,
                                    stadiumName = cStad,
                                    stadiumCapacity = cCap.toIntOrNull() ?: userClub.stadiumCapacity
                                )
                                db.saveOrUpdateClub(updated)
                                onDatabaseEdited("Updated club ${updated.name} in database!")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Club Modifications")
                        }
                    }
                }
            }
            2 -> {
                var newClubName by remember { mutableStateOf("Oran Lions FC") }
                var newClubCountry by remember { mutableStateOf(userClub.countryId) }
                var newClubDiv by remember { mutableStateOf(userClub.divisionId) }
                var newDivName by remember { mutableStateOf("Custom Super League") }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CREATE A CUSTOM CLUB", fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = newClubName, onValueChange = { newClubName = it }, label = { Text("Custom Club Name") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = newClubCountry, onValueChange = { newClubCountry = it.uppercase() }, label = { Text("Country ID (e.g. ALG, ENG)") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = newClubDiv, onValueChange = { newClubDiv = it }, label = { Text("Target Division ID (e.g. ALG_D1)") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        val cid = "CUSTOM_CLUB_${System.currentTimeMillis()}"
                                        val customClub = ClubEntity(
                                            id = cid,
                                            name = newClubName,
                                            shortName = newClubName.take(10),
                                            countryId = newClubCountry,
                                            divisionId = newClubDiv,
                                            city = "Custom City",
                                            primaryColorHex = 0xFF10B981L,
                                            secondaryColorHex = 0xFFFFFFFFL,
                                            reputation = 78,
                                            balance = 45_000_000L,
                                            transferBudget = 25_000_000L,
                                            wageBudgetWeekly = 400_000L,
                                            debt = 0L,
                                            stadiumName = "$newClubName Stadium",
                                            stadiumCapacity = 40000,
                                            ticketPrice = 35,
                                            trainingFacilityLevel = 15,
                                            youthFacilityLevel = 15,
                                            medicalFacilityLevel = 15,
                                            scoutingNetworkLevel = 15,
                                            philosophy = "Youth Development",
                                            rivalClubId = userClub.id,
                                            isFictional = true,
                                            managerName = "Custom Manager"
                                        )
                                        db.saveOrUpdateClub(customClub)
                                        onDatabaseEdited("Created custom club $newClubName in $newClubDiv!")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Create Custom Club & Populate 20-Man Squad")
                                }
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CREATE A NEW PLAYABLE DIVISION", fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = newDivName, onValueChange = { newDivName = it }, label = { Text("New Division Name") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        val did = "${newClubCountry}_CUSTOM_${System.currentTimeMillis() % 1000}"
                                        val div = DivisionEntity(
                                            id = did,
                                            countryId = newClubCountry,
                                            name = newDivName,
                                            tier = 1,
                                            isOfficialStructure = false,
                                            isActiveSim = true
                                        )
                                        db.saveOrUpdateDivision(div)
                                        onDatabaseEdited("Added new playable division: $newDivName ($did)!")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add Custom Division to World Database")
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                var jsonPayload by remember(userClub.divisionId) {
                    mutableStateOf(db.exportCustomDatabaseJson(userClub.divisionId))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text("JSON Database Export / Import (Copy or Paste Authorized Community Databases):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = jsonPayload,
                        onValueChange = { jsonPayload = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text("Division Clubs JSON Payload") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val count = db.importCustomDatabaseJson(jsonPayload)
                                onDatabaseEdited("Imported $count clubs from JSON successfully!")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                db.resetEntireDatabase()
                                onDatabaseEdited("Database reset to factory 420+ divisions.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Factory Reset DB")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// MULTI-SLOT SAVE / LOAD & SETTINGS SCREEN
// =========================================================================
@Composable
fun SaveLoadAndSettingsScreen(
    db: WfmDatabase,
    seasonYear: Int,
    currentWeek: Int,
    manager: ManagerProfile,
    tactics: TacticalConfig,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLoadSave: (Int, Int, String, ManagerProfile) -> Unit
) {
    var saveSlotRefresh by remember { mutableIntStateOf(0) }
    val slots = remember(saveSlotRefresh) {
        val list = mutableListOf<Triple<Int, String, String>>()
        val c = db.readableDatabase.rawQuery("SELECT slotId, saveName, updatedAt FROM save_slots ORDER BY slotId ASC", null)
        while (c.moveToNext()) {
            list.add(Triple(c.getInt(0), c.getString(1), c.getString(2)))
        }
        c.close()
        list
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CAREER SAVE SLOTS & GAME SETTINGS", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Stadium Theme", fontWeight = FontWeight.Bold)
            Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
        }

        listOf(1, 2, 3).forEach { slotNum ->
            val existing = slots.find { it.first == slotNum }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save Slot #$slotNum", fontWeight = FontWeight.Bold)
                        Text(existing?.second ?: "Empty Slot", fontSize = 12.sp, color = Color.Gray)
                        if (existing != null) Text(existing.third, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            val mgrJson = JSONObject().apply {
                                put("name", manager.name)
                                put("nat", manager.nationality)
                                put("clubId", manager.currentClubId)
                                put("intlId", manager.nationalTeamId)
                                put("matches", manager.careerMatches)
                                put("wins", manager.careerWins)
                            }.toString()
                            val cv = ContentValues().apply {
                                put("slotId", slotNum)
                                put("saveName", "${manager.name} (${manager.currentClubId}) - S$seasonYear W$currentWeek")
                                put("seasonYear", seasonYear)
                                put("currentWeek", currentWeek)
                                put("managerJson", mgrJson)
                                put("tacticsJson", tactics.formation)
                                put("shortlistIds", "")
                                put("updatedAt", "Saved Season $seasonYear Week $currentWeek")
                            }
                            db.writableDatabase.insertWithOnConflict("save_slots", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                            saveSlotRefresh++
                        }) {
                            Text("Save")
                        }
                        if (existing != null) {
                            OutlinedButton(onClick = {
                                val c = db.readableDatabase.rawQuery("SELECT seasonYear, currentWeek, managerJson FROM save_slots WHERE slotId = ?", arrayOf(slotNum.toString()))
                                if (c.moveToFirst()) {
                                    val sYear = c.getInt(0)
                                    val sWeek = c.getInt(1)
                                    val mObj = JSONObject(c.getString(2))
                                    val loadedMgr = manager.copy(
                                        name = mObj.optString("name", manager.name),
                                        currentClubId = mObj.optString("clubId", manager.currentClubId),
                                        nationalTeamId = mObj.optString("intlId", ""),
                                        careerMatches = mObj.optInt("matches", 0),
                                        careerWins = mObj.optInt("wins", 0)
                                    )
                                    onLoadSave(sYear, sWeek, loadedMgr.currentClubId, loadedMgr)
                                }
                                c.close()
                            }) {
                                Text("Load")
                            }
                        }
                    }
                }
            }
        }
    }
}
