package com.wfm.game.engine

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.wfm.game.data.*
import kotlin.math.*
import kotlin.random.Random

// =========================================================================
// 1. REAL-TIME 2D TOP-DOWN MATCH ENGINE (22 MOVING NODES + BALL + EVENTS)
// =========================================================================

data class PitchPlayerNode(
    val player: PlayerEntity,
    val isHome: Boolean,
    var x: Float, // 0f..1f (left to right)
    var y: Float, // 0f..1f (top to bottom)
    val baseX: Float,
    val baseY: Float,
    var stamina: Float = 100f,
    var rating: Float = 6.5f,
    var goals: Int = 0,
    var yellowCards: Int = 0,
    var redCard: Boolean = false
)

data class LiveMatchEvent(
    val minute: Int,
    val type: String, // "GOAL", "SAVE", "YELLOW", "RED", "INJURY", "SUB", "MISS", "OFFSIDE", "CORNER"
    val description: String,
    val isHomeEvent: Boolean
)

class Live2DMatchSimulator(
    val fixture: FixtureEntity,
    val homeClub: ClubEntity,
    val awayClub: ClubEntity,
    homeSquad: List<PlayerEntity>,
    awaySquad: List<PlayerEntity>,
    var homeTactics: TacticalConfig,
    var awayTactics: TacticalConfig
) {
    val homeNodes = mutableListOf<PitchPlayerNode>()
    val awayNodes = mutableListOf<PitchPlayerNode>()
    val benchHome = mutableListOf<PlayerEntity>()
    val benchAway = mutableListOf<PlayerEntity>()

    var ballX: Float = 0.5f
    var ballY: Float = 0.5f
    var ballVelX: Float = 0f
    var ballVelY: Float = 0f
    var possessionHome: Boolean = true
    var carrierNode: PitchPlayerNode? = null

    var matchMinute: Int = 0
    var addedTime: Int = 3
    var isHalfTime: Boolean = false
    var isFullTime: Boolean = false
    var homeScore: Int = 0
    var awayScore: Int = 0

    // Detailed Match Stats
    var homeShots: Int = 0
    var awayShots: Int = 0
    var homeShotsOnTarget: Int = 0
    var awayShotsOnTarget: Int = 0
    var homePossessionTicks: Int = 50
    var awayPossessionTicks: Int = 50
    var homeCorners: Int = 0
    var awayCorners: Int = 0
    var homeFouls: Int = 0
    var awayFouls: Int = 0
    var homeSubsUsed: Int = 0

    val events = mutableListOf<LiveMatchEvent>()
    var latestCommentary: String = "Kick-off at ${homeClub.stadiumName}! ${homeClub.name} vs ${awayClub.name}."
    private val rng = Random(System.currentTimeMillis())

    init {
        val sortedHome = homeSquad.filter { it.injuryWeeks == 0 }.sortedByDescending { it.currentAbility }
        val sortedAway = awaySquad.filter { it.injuryWeeks == 0 }.sortedByDescending { it.currentAbility }

        val hStarters = sortedHome.take(11)
        val aStarters = sortedAway.take(11)
        benchHome.addAll(sortedHome.drop(11).take(9))
        benchAway.addAll(sortedAway.drop(11).take(9))

        val hCoords = getFormationCoordinates(homeTactics.formation, isHome = true)
        val aCoords = getFormationCoordinates(awayTactics.formation, isHome = false)

        hStarters.forEachIndexed { idx, p ->
            val (bx, by) = hCoords.getOrElse(idx) { 0.35f to 0.5f }
            homeNodes.add(PitchPlayerNode(p, true, bx, by, bx, by, p.fitness.toFloat()))
        }
        aStarters.forEachIndexed { idx, p ->
            val (bx, by) = aCoords.getOrElse(idx) { 0.65f to 0.5f }
            awayNodes.add(PitchPlayerNode(p, false, bx, by, bx, by, p.fitness.toFloat()))
        }
        carrierNode = homeNodes.getOrNull(9)
    }

    fun getFormationCoordinates(formation: String, isHome: Boolean): List<Pair<Float, Float>> {
        val raw = when (formation) {
            "4-4-2" -> listOf(
                0.06f to 0.5f,
                0.20f to 0.15f, 0.18f to 0.38f, 0.18f to 0.62f, 0.20f to 0.85f,
                0.35f to 0.15f, 0.33f to 0.38f, 0.33f to 0.62f, 0.35f to 0.85f,
                0.46f to 0.36f, 0.46f to 0.64f
            )
            "4-2-3-1" -> listOf(
                0.06f to 0.5f,
                0.20f to 0.15f, 0.18f to 0.38f, 0.18f to 0.62f, 0.20f to 0.85f,
                0.29f to 0.36f, 0.29f to 0.64f,
                0.40f to 0.18f, 0.39f to 0.50f, 0.40f to 0.82f,
                0.47f to 0.50f
            )
            "3-5-2" -> listOf(
                0.06f to 0.5f,
                0.18f to 0.26f, 0.16f to 0.50f, 0.18f to 0.74f,
                0.32f to 0.12f, 0.30f to 0.34f, 0.28f to 0.50f, 0.30f to 0.66f, 0.32f to 0.88f,
                0.46f to 0.36f, 0.46f to 0.64f
            )
            "5-3-2" -> listOf(
                0.06f to 0.5f,
                0.20f to 0.10f, 0.17f to 0.30f, 0.15f to 0.50f, 0.17f to 0.70f, 0.20f to 0.90f,
                0.33f to 0.28f, 0.31f to 0.50f, 0.33f to 0.72f,
                0.46f to 0.36f, 0.46f to 0.64f
            )
            else -> listOf( // 4-3-3
                0.06f to 0.5f,
                0.20f to 0.15f, 0.18f to 0.38f, 0.18f to 0.62f, 0.20f to 0.85f,
                0.32f to 0.28f, 0.29f to 0.50f, 0.32f to 0.72f,
                0.44f to 0.18f, 0.47f to 0.50f, 0.44f to 0.82f
            )
        }
        return if (isHome) raw else raw.map { (1f - it.first) to it.second }
    }

    fun stepSimulationTick() {
        if (isFullTime || isHalfTime) return

        // Advance spatial positions of all 22 players
        val homeMentalityShift = when (homeTactics.mentality) {
            "Very Attacking" -> 0.16f
            "Attacking" -> 0.10f
            "Defensive" -> -0.06f
            "Very Defensive" -> -0.11f
            else -> 0.03f
        }
        val awayMentalityShift = when (awayTactics.mentality) {
            "Very Attacking" -> -0.16f
            "Attacking" -> -0.10f
            "Defensive" -> 0.06f
            "Very Defensive" -> 0.11f
            else -> -0.03f
        }

        // Move players organically toward tactical target + ball attraction
        (homeNodes + awayNodes).forEachIndexed { idx, node ->
            if (node.redCard) {
                node.x = -0.1f; node.y = -0.1f
                return@forEachIndexed
            }
            val isGk = idx == 0 || idx == 11
            if (isGk) {
                node.x = if (node.isHome) 0.04f else 0.96f
                node.y = (0.5f + (ballY - 0.5f) * 0.25f).coerceIn(0.38f, 0.62f)
            } else {
                val shift = if (node.isHome) {
                    if (possessionHome) homeMentalityShift + 0.18f else homeMentalityShift - 0.05f
                } else {
                    if (!possessionHome) awayMentalityShift - 0.18f else awayMentalityShift + 0.05f
                }
                val targetX = (node.baseX + shift + (ballX - 0.5f) * 0.25f).coerceIn(0.08f, 0.92f)
                val targetY = (node.baseY + (ballY - node.baseY) * 0.30f).coerceIn(0.06f, 0.94f)
                val speed = (node.player.attributes.pace / 2200f) * (0.5f + node.stamina / 200f)
                node.x += (targetX - node.x) * speed * 5f + rng.nextFloat() * 0.008f - 0.004f
                node.y += (targetY - node.y) * speed * 5f + rng.nextFloat() * 0.008f - 0.004f
                node.stamina = max(15f, node.stamina - 0.025f)
            }
        }

        // Ball & Possession logic
        val attackers = (if (possessionHome) homeNodes else awayNodes).filter { !it.redCard }
        val defenders = (if (possessionHome) awayNodes else homeNodes).filter { !it.redCard }
        if (possessionHome) homePossessionTicks++ else awayPossessionTicks++

        val currentCarrier = carrierNode ?: attackers.randomOrNull() ?: return
        ballX = currentCarrier.x
        ballY = currentCarrier.y

        // Check for pressing tackle / interception
        val nearestDefender = defenders.drop(1).minByOrNull { hypot(it.x - ballX, it.y - ballY) }
        if (nearestDefender != null && hypot(nearestDefender.x - ballX, nearestDefender.y - ballY) < 0.08f) {
            val tackleScore = nearestDefender.player.attributes.tackling + nearestDefender.player.attributes.anticipation + rng.nextInt(0, 35)
            val dribbleScore = currentCarrier.player.attributes.dribbling + currentCarrier.player.attributes.balance + rng.nextInt(0, 35)
            if (tackleScore > dribbleScore + 12) {
                // Foul check
                if (rng.nextInt(100) < 16) {
                    if (possessionHome) awayFouls++ else homeFouls++
                    latestCommentary = "${matchMinute}' Foul by ${nearestDefender.player.fullName} on ${currentCarrier.player.fullName}!"
                    if (rng.nextInt(100) < 18) {
                        nearestDefender.yellowCards++
                        if (nearestDefender.yellowCards >= 2) {
                            nearestDefender.redCard = true
                            events.add(0, LiveMatchEvent(matchMinute, "RED", "${nearestDefender.player.fullName} sent off (2nd Yellow)!", nearestDefender.isHome))
                        } else {
                            events.add(0, LiveMatchEvent(matchMinute, "YELLOW", "${nearestDefender.player.fullName} booked.", nearestDefender.isHome))
                        }
                    }
                } else {
                    possessionHome = !possessionHome
                    carrierNode = nearestDefender
                    nearestDefender.rating = min(10.0f, nearestDefender.rating + 0.1f)
                    latestCommentary = "${matchMinute}' Crisp tackle by ${nearestDefender.player.fullName} wins possession!"
                    return
                }
            }
        }

        // Advance minute every 3 ticks
        matchMinute++
        if (matchMinute == 45 && !isHalfTime) {
            isHalfTime = true
            latestCommentary = "Half-Time Whistle! ${homeClub.shortName} $homeScore - $awayScore ${awayClub.shortName}."
            return
        }
        if (matchMinute >= 90 + addedTime) {
            isFullTime = true
            latestCommentary = "Full-Time! Final Score: ${homeClub.name} $homeScore - $awayScore ${awayClub.name}."
            return
        }

        // Attacking Zone Resolution (Shooting, Through Balls, Crosses, Offsides, Corners)
        val inShootingZone = (possessionHome && ballX > 0.72f) || (!possessionHome && ballX < 0.28f)
        if (inShootingZone || rng.nextInt(100) < 24) {
            // Offside check
            if (rng.nextInt(100) < 8) {
                latestCommentary = "${matchMinute}' Flag goes up! ${currentCarrier.player.fullName} caught marginally offside."
                possessionHome = !possessionHome
                carrierNode = defenders.firstOrNull()
                return
            }

            // Shot attempt!
            val gk = defenders.firstOrNull() ?: return
            if (possessionHome) homeShots++ else awayShots++

            val shotQuality = (currentCarrier.player.attributes.finishing * 2 +
                    currentCarrier.player.attributes.composure +
                    currentCarrier.player.attributes.technique) / 4 + rng.nextInt(-10, 22)
            val gkSaveScore = (gk.player.attributes.reflexes * 2 +
                    gk.player.attributes.handling +
                    gk.player.attributes.oneOnOnes) / 4 + rng.nextInt(-5, 25)

            if (shotQuality > gkSaveScore + 8) {
                // GOAL!
                if (possessionHome) {
                    homeScore++
                    homeShotsOnTarget++
                } else {
                    awayScore++
                    awayShotsOnTarget++
                }
                currentCarrier.goals++
                currentCarrier.rating = min(10.0f, currentCarrier.rating + 1.1f)
                val msg = "${matchMinute}' GOAL! ${currentCarrier.player.fullName} fires it past ${gk.player.fullName}! ($homeScore-$awayScore)"
                latestCommentary = msg
                events.add(0, LiveMatchEvent(matchMinute, "GOAL", msg, possessionHome))
                possessionHome = !possessionHome
                ballX = 0.5f; ballY = 0.5f
                carrierNode = defenders.lastOrNull()
            } else if (shotQuality > gkSaveScore - 6) {
                // GK SAVE / CORNER
                if (possessionHome) homeShotsOnTarget++ else awayShotsOnTarget++
                gk.rating = min(10.0f, gk.rating + 0.3f)
                if (rng.nextBoolean()) {
                    if (possessionHome) homeCorners++ else awayCorners++
                    latestCommentary = "${matchMinute}' Spectacular fingertip save by ${gk.player.fullName}! Out for a corner."
                } else {
                    latestCommentary = "${matchMinute}' ${gk.player.fullName} holds onto the strike from ${currentCarrier.player.fullName}."
                    possessionHome = !possessionHome
                    carrierNode = gk
                }
            } else {
                latestCommentary = "${matchMinute}' ${currentCarrier.player.fullName} blasts the shot wide of the post."
                possessionHome = !possessionHome
                carrierNode = gk
            }
        } else {
            // Progressive pass to an advanced teammate
            val forwardTeammates = attackers.filter {
                it != currentCarrier && (if (possessionHome) it.x >= currentCarrier.x - 0.05f else it.x <= currentCarrier.x + 0.05f)
            }
            val receiver = forwardTeammates.randomOrNull() ?: attackers.random()
            carrierNode = receiver
            ballX = receiver.x
            ballY = receiver.y
            latestCommentary = "${matchMinute}' ${currentCarrier.player.fullName} threads a pass to ${receiver.player.fullName}."
        }
    }

    fun makeUserSubstitution(outPlayerId: String, inPlayer: PlayerEntity): Boolean {
        if (homeSubsUsed >= 5) return false
        val idx = homeNodes.indexOfFirst { it.player.id == outPlayerId }
        if (idx == -1) return false
        val oldNode = homeNodes[idx]
        homeNodes[idx] = PitchPlayerNode(
            player = inPlayer,
            isHome = true,
            x = oldNode.x,
            y = oldNode.y,
            baseX = oldNode.baseX,
            baseY = oldNode.baseY,
            stamina = 100f,
            rating = 6.5f
        )
        benchHome.removeAll { it.id == inPlayer.id }
        homeSubsUsed++
        events.add(0, LiveMatchEvent(matchMinute, "SUB", "Sub: ${inPlayer.fullName} replaces ${oldNode.player.fullName}", true))
        return true
    }

    fun resumeSecondHalf() {
        if (isHalfTime) {
            isHalfTime = false
            matchMinute = 46
            latestCommentary = "46' Second half underway!"
        }
    }

    fun simulateToFullTimeInstantly() {
        if (isHalfTime) isHalfTime = false
        while (!isFullTime && matchMinute < 95) {
            stepSimulationTick()
            if (isHalfTime) isHalfTime = false
        }
        isFullTime = true
    }

    val homePossessionPercent: Int
        get() = ((homePossessionTicks * 100) / max(1, homePossessionTicks + awayPossessionTicks)).coerceIn(20, 80)
}

// =========================================================================
// 2. WORLD PROGRESSION, TRANSFERS, YOUTH INTAKE, FINANCES & CUPS ENGINE
// =========================================================================

class WorldProgressionEngine(private val database: WfmDatabase) {

    private val rng = Random(System.currentTimeMillis())

    // Commit a finished match to fixtures, standings, player stats, and club finances
    fun commitMatchResult(
        fixture: FixtureEntity,
        homeGoals: Int,
        awayGoals: Int,
        goalScorerIds: List<String> = emptyList(),
        summary: String = "Match completed"
    ) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val fv = ContentValues().apply {
                put("played", 1)
                put("homeGoals", homeGoals)
                put("awayGoals", awayGoals)
                put("summaryText", summary)
            }
            db.update("fixtures", fv, "id = ?", arrayOf(fixture.id))

            if (!fixture.isCup) {
                updateStandingTeam(db, fixture.homeClubId, fixture.homeClubName, fixture.competitionId, homeGoals, awayGoals)
                updateStandingTeam(db, fixture.awayClubId, fixture.awayClubName, fixture.competitionId, awayGoals, homeGoals)
            }

            // Update scorers
            for (pid in goalScorerIds) {
                db.execSQL("UPDATE players SET seasonGoals = seasonGoals + 1, careerGoals = careerGoals + 1, seasonApps = seasonApps + 1 WHERE id = ?", arrayOf(pid))
            }

            // Matchday ticket income for home club
            val homeClub = database.getClubById(fixture.homeClubId)
            if (homeClub != null) {
                val attendance = ((homeClub.stadiumCapacity * (55 + homeClub.reputation / 2)) / 100).coerceAtMost(homeClub.stadiumCapacity)
                val gateReceipt = attendance.toLong() * homeClub.ticketPrice.toLong()
                val newBalance = homeClub.balance + gateReceipt - homeClub.wageBudgetWeekly
                val cv = ContentValues().apply { put("balance", newBalance) }
                db.update("clubs", cv, "id = ?", arrayOf(homeClub.id))
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun updateStandingTeam(
        db: SQLiteDatabase,
        clubId: String,
        clubName: String,
        divisionId: String,
        gf: Int,
        ga: Int
    ) {
        val win = if (gf > ga) 1 else 0
        val draw = if (gf == ga) 1 else 0
        val loss = if (gf < ga) 1 else 0
        val pts = win * 3 + draw

        db.execSQL(
            """
            INSERT INTO standings (clubId, clubName, divisionId, played, won, drawn, lost, goalsFor, goalsAgainst, points)
            VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(clubId, divisionId) DO UPDATE SET
                played = played + 1,
                won = won + $win,
                drawn = drawn + $draw,
                lost = lost + $loss,
                goalsFor = goalsFor + $gf,
                goalsAgainst = goalsAgainst + $ga,
                points = points + $pts
            """.trimIndent(),
            arrayOf(clubId, clubName, divisionId, win, draw, loss, gf, ga, pts)
        )
    }

    // Simulate all other matches in the current week across active leagues
    fun simulateGlobalWeek(
        currentWeek: Int,
        seasonYear: Int,
        userClubId: String,
        userDivisionId: String
    ): List<String> {
        val highlights = mutableListOf<String>()
        val db = database.writableDatabase

        // 1. Simulate active division matches for this week (except user's match if not yet played)
        val cursor = db.rawQuery(
            "SELECT * FROM fixtures WHERE weekNumber = ? AND played = 0",
            arrayOf(currentWeek.toString())
        )
        val pendingFixtures = mutableListOf<FixtureEntity>()
        while (cursor.moveToNext()) {
            pendingFixtures.add(
                FixtureEntity(
                    id = cursor.getString(0),
                    competitionId = cursor.getString(1),
                    competitionName = cursor.getString(2),
                    isCup = cursor.getInt(3) == 1,
                    roundNumber = cursor.getInt(4),
                    weekNumber = cursor.getInt(5),
                    homeClubId = cursor.getString(6),
                    homeClubName = cursor.getString(7),
                    awayClubId = cursor.getString(8),
                    awayClubName = cursor.getString(9),
                    played = false
                )
            )
        }
        cursor.close()

        for (fix in pendingFixtures) {
            if (fix.homeClubId == userClubId || fix.awayClubId == userClubId) continue
            val hClub = database.getClubById(fix.homeClubId)
            val aClub = database.getClubById(fix.awayClubId)
            val hRep = (hClub?.reputation ?: 60) + 4 // Home advantage
            val aRep = aClub?.reputation ?: 60
            val diff = (hRep - aRep).coerceIn(-30, 30)

            val hGoals = max(0, (rng.nextInt(0, 3) + (if (diff > 8) 1 else 0) + (if (rng.nextInt(100) < 25 + diff) 1 else 0)))
            val aGoals = max(0, (rng.nextInt(0, 3) + (if (diff < -8) 1 else 0) + (if (rng.nextInt(100) < 25 - diff) 1 else 0)))

            commitMatchResult(fix, hGoals, aGoals, emptyList(), "${fix.homeClubName} $hGoals - $aGoals ${fix.awayClubName}")
            if (fix.competitionId == userDivisionId && highlights.size < 5) {
                highlights.add("${fix.homeClubName} $hGoals - $aGoals ${fix.awayClubName}")
            }
        }

        // 2. AI Transfer Market Activity during Transfer Windows (Weeks 1..5 and 16..20)
        if (currentWeek in 1..5 || currentWeek in 16..20) {
            if (rng.nextInt(100) < 55) {
                executeAiTransferDeal(seasonYear, currentWeek)
            }
        }

        // 3. Weekly Player Training, Fitness Recovery & Injury Healing
        db.execSQL("UPDATE players SET fitness = MIN(100, fitness + 9), injuryWeeks = MAX(0, injuryWeeks - 1)")

        // 4. National & Continental Cup Milestones
        if (currentWeek == 10 || currentWeek == 20 || currentWeek == 28) {
            simulateCupAndContinentalRound(seasonYear, currentWeek, userClubId)
        }

        return highlights
    }

    private fun executeAiTransferDeal(seasonYear: Int, currentWeek: Int) {
        val candidates = database.searchPlayers(minOverall = 72, limit = 40)
        if (candidates.isEmpty()) return
        val player = candidates.random(rng)
        val clubs = database.searchClubs("", limit = 30).filter { it.id != player.clubId && it.transferBudget >= player.marketValue }
        val buyer = clubs.randomOrNull(rng) ?: return

        val fee = player.marketValue
        val updatedPlayer = player.copy(
            clubId = buyer.id,
            wageWeekly = (player.wageWeekly * 115) / 100,
            isTransferListed = false
        )
        database.saveOrUpdatePlayer(updatedPlayer)
        database.saveOrUpdateClub(buyer.copy(
            transferBudget = buyer.transferBudget - fee,
            balance = buyer.balance - fee
        ))

        database.insertInboxMessage(
            database.writableDatabase,
            InboxMessage(
                id = "AI_TRF_${seasonYear}_${currentWeek}_${rng.nextInt(9999)}",
                week = currentWeek,
                seasonYear = seasonYear,
                category = "Transfer",
                title = "World Transfer: ${player.fullName} joins ${buyer.name}",
                body = "${buyer.name} have completed the permanent signing of ${player.position} ${player.fullName} (OVR ${player.currentAbility}) for €${fee / 1_000_000}M."
            )
        )
    }

    private fun simulateCupAndContinentalRound(seasonYear: Int, currentWeek: Int, userClubId: String) {
        val userClub = database.getClubById(userClubId) ?: return
        val country = WorldFootballCatalog.getAllCountries().find { it.id == userClub.countryId } ?: return
        val roundName = when (currentWeek) {
            10 -> "Quarter-Finals"
            20 -> "Semi-Finals"
            else -> "Grand Final"
        }
        val domesticPeers = database.searchClubs(userClub.countryId, limit = 16).filter { it.id != userClubId }
        val opponent = domesticPeers.randomOrNull(rng) ?: return
        val userScore = rng.nextInt(0, 4) + (if (userClub.reputation >= opponent.reputation) 1 else 0)
        val oppScore = rng.nextInt(0, 3)

        val cupOutcome = if (userScore >= oppScore) {
            val prize = 750_000L
            database.saveOrUpdateClub(userClub.copy(balance = userClub.balance + prize, transferBudget = userClub.transferBudget + prize / 2))
            "${userClub.name} defeated ${opponent.name} ($userScore-$oppScore) in the ${country.domesticCupName} $roundName and earned €750K prize money!"
        } else {
            "${userClub.name} were edged out by ${opponent.name} ($userScore-$oppScore) in the ${country.domesticCupName} $roundName."
        }

        database.insertInboxMessage(
            database.writableDatabase,
            InboxMessage(
                id = "CUP_${seasonYear}_${currentWeek}",
                week = currentWeek,
                seasonYear = seasonYear,
                category = "Cup Draw",
                title = "${country.domesticCupName} & ${country.confederation.champCup} Update",
                body = cupOutcome
            )
        )
    }

    // Negotiate a Player Transfer or Loan on behalf of the User's Club
    fun negotiateTransfer(
        buyerClub: ClubEntity,
        targetPlayer: PlayerEntity,
        offeredFee: Long,
        offeredWeeklyWage: Long,
        isLoanOffer: Boolean
    ): Pair<Boolean, String> {
        if (targetPlayer.clubId == buyerClub.id) {
            return false to "${targetPlayer.fullName} is already registered at ${buyerClub.name}."
        }
        val sellingClub = database.getClubById(targetPlayer.clubId)

        if (isLoanOffer) {
            val loanFee = max(50_000L, targetPlayer.marketValue / 15)
            if (buyerClub.transferBudget < loanFee) {
                return false to "Insufficient transfer budget to cover the €${loanFee / 1000}K loan fee."
            }
            if (!targetPlayer.isLoanListed && targetPlayer.age > 23 && targetPlayer.currentAbility >= (sellingClub?.reputation ?: 70)) {
                return false to "${sellingClub?.name ?: "The club"} refuse to loan out a first-team starter."
            }
            val loanedPlayer = targetPlayer.copy(
                loanedFromClubId = targetPlayer.clubId,
                clubId = buyerClub.id,
                isLoanListed = false
            )
            database.saveOrUpdatePlayer(loanedPlayer)
            database.saveOrUpdateClub(buyerClub.copy(
                transferBudget = buyerClub.transferBudget - loanFee,
                balance = buyerClub.balance - loanFee
            ))
            return true to "Loan Deal Agreed! ${targetPlayer.fullName} joins ${buyerClub.name} on season-long loan."
        }

        // Permanent transfer evaluation
        if (offeredFee > buyerClub.transferBudget) {
            return false to "Your offer of €${offeredFee / 1000}K exceeds your available Transfer Budget (€${buyerClub.transferBudget / 1000}K)."
        }

        val requiredClubFee = if (targetPlayer.isTransferListed) {
            (targetPlayer.marketValue * 80) / 100
        } else {
            (targetPlayer.marketValue * 105) / 100
        }

        if (offeredFee < requiredClubFee && offeredFee < targetPlayer.releaseClause) {
            return false to "${sellingClub?.name ?: "Selling club"} rejected your €${offeredFee / 1000}K bid. They demand at least €${requiredClubFee / 1000}K (or the €${targetPlayer.releaseClause / 1000}K release clause)."
        }

        val minWageExpected = (targetPlayer.wageWeekly * 95) / 100
        if (offeredWeeklyWage < minWageExpected) {
            return false to "${targetPlayer.fullName}'s agent rejected the contract terms, requesting at least €${minWageExpected}/wk."
        }

        // Complete transfer!
        val transferred = targetPlayer.copy(
            clubId = buyerClub.id,
            wageWeekly = offeredWeeklyWage,
            contractExpiryYear = targetPlayer.contractExpiryYear + 3,
            isTransferListed = false,
            morale = 90
        )
        database.saveOrUpdatePlayer(transferred)
        database.saveOrUpdateClub(buyerClub.copy(
            transferBudget = buyerClub.transferBudget - offeredFee,
            balance = buyerClub.balance - offeredFee
        ))
        if (sellingClub != null) {
            database.saveOrUpdateClub(sellingClub.copy(
                transferBudget = sellingClub.transferBudget + offeredFee,
                balance = sellingClub.balance + offeredFee
            ))
        }
        return true to "Transfer Completed! ${targetPlayer.fullName} has signed for ${buyerClub.name} for €${offeredFee / 1000}K!"
    }

    // End-of-Season Rollover: Champions History, Promotion/Relegation, Aging, Retirements & Newgens
    fun processEndOfSeasonRollover(
        currentSeasonYear: Int,
        userDivisionId: String,
        userClubId: String
    ): String {
        val db = database.writableDatabase
        val summaryLines = mutableListOf<String>()

        db.beginTransaction()
        try {
            val allDivs = database.getAllDivisions()
            val byCountry = allDivs.groupBy { it.countryId }

            for ((countryId, divs) in byCountry) {
                val sortedTiers = divs.sortedBy { it.tier }
                for (i in sortedTiers.indices) {
                    val div = sortedTiers[i]
                    val table = database.getStandings(div.id)
                    if (table.size >= 2) {
                        val champ = table[0]
                        val runnerUp = table[1]

                        // Find top scorer in division
                        val scorerCursor = db.rawQuery(
                            "SELECT fullName, seasonGoals FROM players WHERE clubId IN (SELECT id FROM clubs WHERE divisionId = ?) ORDER BY seasonGoals DESC LIMIT 1",
                            arrayOf(div.id)
                        )
                        var topScorer = "N/A"
                        var topGoals = 18
                        if (scorerCursor.moveToFirst()) {
                            topScorer = scorerCursor.getString(0)
                            topGoals = max(12, scorerCursor.getInt(1))
                        }
                        scorerCursor.close()

                        // Record Historical Archive for Tier 1 or User's Division
                        if (div.tier == 1 || div.id == userDivisionId) {
                            val hv = ContentValues().apply {
                                put("seasonYear", currentSeasonYear)
                                put("competitionId", div.id)
                                put("competitionName", div.name)
                                put("winnerClubName", champ.clubName)
                                put("runnerUpClubName", runnerUp.clubName)
                                put("topScorerName", topScorer)
                                put("topScorerGoals", topGoals)
                            }
                            db.insert("history_records", null, hv)
                        }
                        if (div.id == userDivisionId) {
                            summaryLines.add("${div.name} Champion: ${champ.clubName} (${champ.points} pts)")
                        }

                        // Promotion & Relegation Swap between Tier i and Tier i+1
                        if (i + 1 < sortedTiers.size) {
                            val lowerDiv = sortedTiers[i + 1]
                            val lowerTable = database.getStandings(lowerDiv.id)
                            if (table.size >= 4 && lowerTable.size >= 4) {
                                val relegated = table.takeLast(2)
                                val promoted = lowerTable.take(2)

                                relegated.forEach { row ->
                                    db.execSQL("UPDATE clubs SET divisionId = ? WHERE id = ?", arrayOf(lowerDiv.id, row.clubId))
                                    if (row.clubId == userClubId) summaryLines.add("RELEGATED to ${lowerDiv.name}!")
                                }
                                promoted.forEach { row ->
                                    db.execSQL("UPDATE clubs SET divisionId = ? WHERE id = ?", arrayOf(div.id, row.clubId))
                                    if (row.clubId == userClubId) summaryLines.add("PROMOTED to ${div.name}!")
                                }
                            }
                        }
                    }
                }
            }

            // Age all players by +1 year, develop U-24 prospects, decline 32+ veterans, retire 36+
            db.execSQL("UPDATE players SET age = age + 1, seasonGoals = 0, seasonAssists = 0, seasonApps = 0")
            db.execSQL("UPDATE players SET currentAbility = MIN(potentialAbility, currentAbility + 2) WHERE age <= 24")
            db.execSQL("UPDATE players SET currentAbility = MAX(35, currentAbility - 2) WHERE age >= 33")
            db.execSQL("DELETE FROM players WHERE age >= 38")

            // Generate Youth Academy Intake (Newgens) for user's club
            val userClub = database.getClubById(userClubId)
            if (userClub != null) {
                val country = WorldFootballCatalog.getAllCountries().find { it.id == userClub.countryId }
                    ?: WorldFootballCatalog.COUNTRIES.first()
                for (nIdx in 1..3) {
                    val pos = listOf("ST", "CM", "CB", "LW", "GK", "AM").random(rng)
                    val base = (userClub.youthFacilityLevel * 3 + rng.nextInt(15, 28)).coerceIn(45, 76)
                    val attrs = database.generateAttributesForPosition(pos, base, rng)
                    val ca = attrs.calculateRoleOverall(pos)
                    val pa = (ca + rng.nextInt(14, 28)).coerceIn(ca + 5, 98)
                    val newgen = PlayerEntity(
                        id = "${userClubId}_NEWGEN_${currentSeasonYear + 1}_$nIdx",
                        fullName = "${country.firstNames.random(rng)} ${country.lastNames.random(rng)}",
                        birthYear = currentSeasonYear + 1 - 16,
                        age = 16,
                        nationality = country.id,
                        secondNationality = "",
                        position = pos,
                        secondaryPosition = "CM",
                        preferredFoot = if (rng.nextBoolean()) "Right" else "Left",
                        heightCm = rng.nextInt(172, 193),
                        weightKg = rng.nextInt(65, 82),
                        clubId = userClubId,
                        squadNumber = 40 + nIdx,
                        wageWeekly = 650L,
                        contractExpiryYear = currentSeasonYear + 4,
                        releaseClause = 5_000_000L,
                        marketValue = 450_000L,
                        morale = 92,
                        fitness = 100,
                        sharpness = 85,
                        reputation = ca,
                        personality = "Driven Wonderkid",
                        injuryWeeks = 0,
                        seasonGoals = 0,
                        seasonAssists = 0,
                        seasonApps = 0,
                        careerGoals = 0,
                        intlCaps = 0,
                        intlGoals = 0,
                        currentAbility = ca,
                        potentialAbility = pa,
                        isYouthAcademy = true,
                        isTransferListed = false,
                        isLoanListed = false,
                        isFictional = true,
                        attributes = attrs
                    )
                    database.saveOrUpdatePlayer(newgen)
                }
                summaryLines.add("3 new 16-year-old Academy Newgens promoted to your Youth Intake!")
            }

            // Rebuild standings & fixtures for the new season
            db.execSQL("DELETE FROM standings")
            db.execSQL("DELETE FROM fixtures")
            val clubsCursor = db.rawQuery("SELECT id, name, divisionId FROM clubs", null)
            while (clubsCursor.moveToNext()) {
                val sv = ContentValues().apply {
                    put("clubId", clubsCursor.getString(0))
                    put("clubName", clubsCursor.getString(1))
                    put("divisionId", clubsCursor.getString(2))
                    put("played", 0); put("won", 0); put("drawn", 0); put("lost", 0)
                    put("goalsFor", 0); put("goalsAgainst", 0); put("points", 0)
                }
                db.insert("standings", null, sv)
            }
            clubsCursor.close()

            val activeDivs = database.getAllDivisions().filter { it.isActiveSim || it.id == userDivisionId }
            activeDivs.forEach { d ->
                database.generateFixturesForDivision(db, d.id, d.name)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return summaryLines.joinToString("\n• ", prefix = "• ")
    }
}
