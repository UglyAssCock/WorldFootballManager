package com.wfm.game.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// =========================================================================
// 1. CORE DATA MODELS (COUNTRIES, 400+ DIVISIONS, CLUBS, PLAYERS, TACTICS)
// =========================================================================

enum class Confederation(val code: String, val fullName: String, val champCup: String, val secondCup: String) {
    UEFA("UEFA", "Europe", "UEFA Champions League", "UEFA Europa & Conference League"),
    CAF("CAF", "Africa", "CAF Champions League", "CAF Confederation Cup"),
    CONMEBOL("CONMEBOL", "South America", "Copa Libertadores", "Copa Sudamericana"),
    CONCACAF("CONCACAF", "North & Central America", "CONCACAF Champions Cup", "CONCACAF Central Cup"),
    AFC("AFC", "Asia", "AFC Champions League Elite", "AFC Champions League Two"),
    OFC("OFC", "Oceania", "OFC Champions League", "OFC President's Shield")
}

data class CountryInfo(
    val id: String,
    val name: String,
    val confederation: Confederation,
    val fifaRanking: Int,
    val domesticCupName: String,
    val superCupName: String,
    val verifiedDivisions: List<String>,
    val totalTargetDivisions: Int,
    val sampleVerifiedClubs: List<String>,
    val firstNames: List<String>,
    val lastNames: List<String>
)

data class DivisionEntity(
    val id: String,
    val countryId: String,
    val name: String,
    val tier: Int,
    val groupIndex: Int = 1,
    val teamCount: Int = 20,
    val promotionSpots: Int = 2,
    val playoffSpots: Int = 4,
    val relegationSpots: Int = 3,
    val pointsForWin: Int = 3,
    val prizePool: Long = 5_000_000L,
    val isOfficialStructure: Boolean = true,
    val isActiveSim: Boolean = true
)

data class ClubEntity(
    val id: String,
    val name: String,
    val shortName: String,
    val countryId: String,
    val divisionId: String,
    val city: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val reputation: Int, // 1..100
    val balance: Long,
    val transferBudget: Long,
    val wageBudgetWeekly: Long,
    val debt: Long,
    val stadiumName: String,
    val stadiumCapacity: Int,
    val ticketPrice: Int,
    val trainingFacilityLevel: Int, // 1..20
    val youthFacilityLevel: Int,    // 1..20
    val medicalFacilityLevel: Int,  // 1..20
    val scoutingNetworkLevel: Int,  // 1..20
    val philosophy: String,         // "Youth Development", "Galacticos", "Balanced", "Moneyball"
    val rivalClubId: String,
    val isFictional: Boolean,
    val managerName: String,
    val formation: String = "4-3-3"
)

data class PlayerAttributes(
    // Technical (12)
    val passing: Int,
    val finishing: Int,
    val dribbling: Int,
    val crossing: Int,
    val firstTouch: Int,
    val tackling: Int,
    val marking: Int,
    val heading: Int,
    val longShots: Int,
    val technique: Int,
    val setPieces: Int,
    val penalties: Int,
    // Mental (12)
    val decisions: Int,
    val vision: Int,
    val composure: Int,
    val anticipation: Int,
    val concentration: Int,
    val positioning: Int,
    val teamwork: Int,
    val leadership: Int,
    val aggression: Int,
    val workRate: Int,
    val determination: Int,
    val offTheBall: Int,
    // Physical (8)
    val acceleration: Int,
    val pace: Int,
    val stamina: Int,
    val strength: Int,
    val agility: Int,
    val balance: Int,
    val jumping: Int,
    val naturalFitness: Int,
    // Goalkeeping (6)
    val reflexes: Int,
    val handling: Int,
    val gkPositioning: Int,
    val aerialAbility: Int,
    val distribution: Int,
    val oneOnOnes: Int,
    // Hidden (3)
    val consistency: Int = 70,
    val bigMatches: Int = 70,
    val injuryProneness: Int = 25
) {
    fun calculateRoleOverall(position: String): Int {
        return when (position) {
            "GK" -> ((reflexes * 3 + handling * 2 + gkPositioning * 2 + oneOnOnes * 2 + aerialAbility + distribution + composure + agility) / 13).coerceIn(1, 99)
            "CB" -> ((tackling * 3 + marking * 3 + positioning * 2 + strength * 2 + heading * 2 + jumping + anticipation + concentration + pace) / 16).coerceIn(1, 99)
            "LB", "RB" -> ((tackling * 2 + marking * 2 + pace * 2 + stamina * 2 + crossing * 2 + positioning + acceleration + dribbling + workRate) / 14).coerceIn(1, 99)
            "DM" -> ((tackling * 2 + positioning * 2 + passing * 2 + decisions * 2 + stamina * 2 + workRate + strength + anticipation + teamwork) / 14).coerceIn(1, 99)
            "CM" -> ((passing * 3 + vision * 2 + decisions * 2 + firstTouch * 2 + stamina * 2 + technique + workRate + tackling + offTheBall) / 15).coerceIn(1, 99)
            "AM" -> ((vision * 3 + passing * 2 + technique * 2 + dribbling * 2 + firstTouch * 2 + decisions + offTheBall + longShots + agility) / 15).coerceIn(1, 99)
            "LW", "RW" -> ((pace * 3 + acceleration * 2 + dribbling * 3 + crossing * 2 + technique * 2 + offTheBall + finishing + agility) / 15).coerceIn(1, 99)
            else -> ((finishing * 3 + offTheBall * 2 + composure * 2 + firstTouch * 2 + acceleration * 2 + pace + heading + strength + technique) / 15).coerceIn(1, 99)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("pas", passing); put("fin", finishing); put("dri", dribbling); put("cro", crossing)
        put("ft", firstTouch); put("tac", tackling); put("mar", marking); put("hea", heading)
        put("ls", longShots); put("tec", technique); put("sp", setPieces); put("pen", penalties)
        put("dec", decisions); put("vis", vision); put("com", composure); put("ant", anticipation)
        put("con", concentration); put("pos", positioning); put("tea", teamwork); put("lea", leadership)
        put("agg", aggression); put("wor", workRate); put("det", determination); put("otb", offTheBall)
        put("acc", acceleration); put("pac", pace); put("sta", stamina); put("str", strength)
        put("agi", agility); put("bal", balance); put("jum", jumping); put("nat", naturalFitness)
        put("ref", reflexes); put("han", handling); put("gkp", gkPositioning); put("aer", aerialAbility)
        put("dis", distribution); put("ooo", oneOnOnes)
        put("cns", consistency); put("big", bigMatches); put("inj", injuryProneness)
    }

    companion object {
        fun fromJson(o: JSONObject): PlayerAttributes = PlayerAttributes(
            passing = o.optInt("pas", 50), finishing = o.optInt("fin", 50),
            dribbling = o.optInt("dri", 50), crossing = o.optInt("cro", 50),
            firstTouch = o.optInt("ft", 50), tackling = o.optInt("tac", 50),
            marking = o.optInt("mar", 50), heading = o.optInt("hea", 50),
            longShots = o.optInt("ls", 50), technique = o.optInt("tec", 50),
            setPieces = o.optInt("sp", 50), penalties = o.optInt("pen", 50),
            decisions = o.optInt("dec", 50), vision = o.optInt("vis", 50),
            composure = o.optInt("com", 50), anticipation = o.optInt("ant", 50),
            concentration = o.optInt("con", 50), positioning = o.optInt("pos", 50),
            teamwork = o.optInt("tea", 50), leadership = o.optInt("lea", 50),
            aggression = o.optInt("agg", 50), workRate = o.optInt("wor", 50),
            determination = o.optInt("det", 50), offTheBall = o.optInt("otb", 50),
            acceleration = o.optInt("acc", 50), pace = o.optInt("pac", 50),
            stamina = o.optInt("sta", 50), strength = o.optInt("str", 50),
            agility = o.optInt("agi", 50), balance = o.optInt("bal", 50),
            jumping = o.optInt("jum", 50), naturalFitness = o.optInt("nat", 50),
            reflexes = o.optInt("ref", 20), handling = o.optInt("han", 20),
            gkPositioning = o.optInt("gkp", 20), aerialAbility = o.optInt("aer", 20),
            distribution = o.optInt("dis", 20), oneOnOnes = o.optInt("ooo", 20),
            consistency = o.optInt("cns", 70), bigMatches = o.optInt("big", 70),
            injuryProneness = o.optInt("inj", 25)
        )
    }
}

data class PlayerEntity(
    val id: String,
    val fullName: String,
    val birthYear: Int,
    val age: Int,
    val nationality: String,
    val secondNationality: String,
    val position: String,          // GK, CB, LB, RB, DM, CM, AM, LW, RW, ST
    val secondaryPosition: String,
    val preferredFoot: String,     // Right, Left, Both
    val heightCm: Int,
    val weightKg: Int,
    val clubId: String,
    val squadNumber: Int,
    val wageWeekly: Long,
    val contractExpiryYear: Int,
    val releaseClause: Long,
    val marketValue: Long,
    val morale: Int,               // 1..100
    val fitness: Int,              // 1..100
    val sharpness: Int,            // 1..100
    val reputation: Int,           // 1..100
    val personality: String,       // Model Citizen, Driven, Ambitious, Professional, Balanced, Volatile
    val injuryWeeks: Int,
    val seasonGoals: Int,
    val seasonAssists: Int,
    val seasonApps: Int,
    val careerGoals: Int,
    val intlCaps: Int,
    val intlGoals: Int,
    val currentAbility: Int,
    val potentialAbility: Int,
    val isYouthAcademy: Boolean,
    val isTransferListed: Boolean,
    val isLoanListed: Boolean,
    val loanedFromClubId: String = "",
    val isFictional: Boolean = true,
    val attributes: PlayerAttributes
)

data class StandingRow(
    val clubId: String,
    val clubName: String,
    val divisionId: String,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val points: Int = 0
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class FixtureEntity(
    val id: String,
    val competitionId: String,
    val competitionName: String,
    val isCup: Boolean,
    val roundNumber: Int,
    val weekNumber: Int,
    val homeClubId: String,
    val homeClubName: String,
    val awayClubId: String,
    val awayClubName: String,
    val played: Boolean = false,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val penaltyWinnerId: String = "",
    val summaryText: String = ""
)

data class TacticalConfig(
    val formation: String = "4-3-3",
    val mentality: String = "Balanced",          // Very Defensive, Defensive, Balanced, Attacking, Very Attacking
    val passingStyle: String = "Short Passing",  // Short Passing, Direct Passing, Possession, Counterattacking, Wing Play
    val pressingStyle: String = "High Press",    // High Press, Mid-Block, Low Block, Counter-Press
    val defensiveLine: String = "Standard",      // High Line, Standard, Deep Line, Offside Trap
    val transitionStyle: String = "Counterattack",// Counterattack, Regroup, Distribute Quickly
    val playerInstruction: String = "Work Ball Into Box", // Work Ball Into Box, Shoot On Sight, Overlap Fullbacks, Run In Behind
    val captainId: String = "",
    val penaltyTakerId: String = "",
    val freeKickTakerId: String = "",
    val familiarity: Int = 82
)

data class InboxMessage(
    val id: String,
    val week: Int,
    val seasonYear: Int,
    val category: String, // "Transfer", "Board", "Youth", "Scout", "Media", "Cup Draw", "Injury"
    val title: String,
    val body: String,
    val isRead: Boolean = false
)

data class HistoricalRecord(
    val seasonYear: Int,
    val competitionId: String,
    val competitionName: String,
    val winnerClubName: String,
    val runnerUpClubName: String,
    val topScorerName: String,
    val topScorerGoals: Int
)

data class ManagerProfile(
    val name: String,
    val nationality: String,
    val age: Int = 36,
    val currentClubId: String,
    val nationalTeamId: String = "",
    val reputation: Int = 55,
    val coachingBadge: String = "Continental Pro License",
    val careerMatches: Int = 0,
    val careerWins: Int = 0,
    val careerDraws: Int = 0,
    val careerLosses: Int = 0,
    val trophiesWon: Int = 0,
    val promotionsWon: Int = 0,
    val relegationsSuffered: Int = 0,
    val boardConfidence: Int = 80,
    val fanConfidence: Int = 78,
    val activeChallenge: String = "None (Standard Sandbox Career)"
) {
    val winPercentage: Int get() = if (careerMatches == 0) 0 else (careerWins * 100) / careerMatches
}

// =========================================================================
// 2. WORLD CATALOG: 60+ NATIONS & 420+ PLAYABLE DIVISIONS GENERATOR
// =========================================================================

object WorldFootballCatalog {

    val COUNTRIES: List<CountryInfo> = listOf(
        // EUROPE (UEFA) - Including England's 10-tier pyramid
        CountryInfo(
            id = "ENG", name = "England", confederation = Confederation.UEFA, fifaRanking = 4,
            domesticCupName = "FA Cup", superCupName = "Community Shield",
            verifiedDivisions = listOf(
                "English Premier Division", "EFL Championship", "EFL League One", "EFL League Two",
                "National League", "National League North", "National League South",
                "Northern Premier Division", "Southern League Central", "Isthmian Premier Division"
            ),
            totalTargetDivisions = 12,
            sampleVerifiedClubs = listOf("Manchester Sky", "London Arsenal", "Liverpool Reds", "Chelsea Blues", "Aston Villa", "Newcastle Magpies", "Tottenham Lillywhites", "Brighton Seagulls", "Leeds United", "Sheffield Blades", "Sunderland Black Cats", "Ipswich Tractor", "Wrexham Dragons", "Notts County", "Portsmouth", "Derby Rams", "Bolton Wanderers", "Blackburn Rovers", "Middlesbrough", "Coventry Sky"),
            firstNames = listOf("Harry", "Jude", "Bukayo", "Cole", "Declan", "Phil", "Jack", "Ollie", "Kobbie", "Trent", "Marcus", "Jordan"),
            lastNames = listOf("Kane", "Bellingham", "Saka", "Palmer", "Rice", "Foden", "Walker", "Watkins", "Mainoo", "Sterling", "Pickford", "Wilson")
        ),
        CountryInfo(
            id = "ESP", name = "Spain", confederation = Confederation.UEFA, fifaRanking = 1,
            domesticCupName = "Copa del Rey", superCupName = "Supercopa de España",
            verifiedDivisions = listOf("La Liga Primera", "Segunda División", "Primera Federación A", "Primera Federación B", "Segunda Federación I", "Segunda Federación II", "Tercera Regional I", "Tercera Regional II"),
            totalTargetDivisions = 10,
            sampleVerifiedClubs = listOf("Real Madrid", "FC Barcelona", "Atlético Madrid", "Athletic Bilbao", "Real Sociedad", "Villarreal", "Real Betis", "Sevilla FC", "Valencia CF", "Girona FC", "Celta Vigo", "Osasuna", "Real Zaragoza", "Deportivo Coruna", "Racing Santander", "Malaga CF"),
            firstNames = listOf("Lamine", "Pedri", "Rodri", "Alvaro", "Dani", "Nico", "Unai", "Pau", "Gavi", "Ferran", "Mikel", "Alex"),
            lastNames = listOf("Yamal", "Gonzalez", "Hernandez", "Morata", "Olmo", "Williams", "Simon", "Cubarsi", "Torres", "Merino", "Baena", "Garcia")
        ),
        CountryInfo(
            id = "GER", name = "Germany", confederation = Confederation.UEFA, fifaRanking = 10,
            domesticCupName = "DFB-Pokal", superCupName = "DFL-Supercup",
            verifiedDivisions = listOf("Bundesliga", "2. Bundesliga", "3. Liga", "Regionalliga Nord", "Regionalliga Nordost", "Regionalliga West", "Regionalliga Südwest", "Regionalliga Bayern", "Oberliga Nord", "Oberliga Süd"),
            totalTargetDivisions = 10,
            sampleVerifiedClubs = listOf("Bayern Munich", "Bayer Leverkusen", "Borussia Dortmund", "RB Leipzig", "VfB Stuttgart", "Eintracht Frankfurt", "SC Freiburg", "Werder Bremen", "Hamburger SV", "Schalke 04", "FC Köln", "Hertha BSC", "Kaiserslautern", "Dynamo Dresden"),
            firstNames = listOf("Jamal", "Florian", "Kai", "Joshua", "Manuel", "Toni", "Ilkay", "Leroy", "Niclas", "Maximilian", "Aleksandar", "deniz"),
            lastNames = listOf("Musiala", "Wirtz", "Havertz", "Kimmich", "Neuer", "Rüdiger", "Sane", "Füllkrug", "Beier", "Pavlovic", "Müller", "Tah")
        ),
        CountryInfo(
            id = "ITA", name = "Italy", confederation = Confederation.UEFA, fifaRanking = 9,
            domesticCupName = "Coppa Italia", superCupName = "Supercoppa Italiana",
            verifiedDivisions = listOf("Serie A", "Serie B", "Serie C Girone A", "Serie C Girone B", "Serie C Girone C", "Serie D Girone A", "Serie D Girone B", "Serie D Girone C", "Eccellenza Nord", "Eccellenza Sud"),
            totalTargetDivisions = 10,
            sampleVerifiedClubs = listOf("Inter Milan", "AC Milan", "Juventus", "Napoli", "AS Roma", "Atalanta", "Lazio", "Fiorentina", "Bologna", "Torino", "Palermo", "Sampdoria", "Parma", "Como 1907", "Bari", "Catania"),
            firstNames = listOf("Nicolo", "Alessandro", "Federico", "Gianluigi", "Lorenzo", "Riccardo", "Matteo", "Davide", "Sandro", "Andrea", "Giacomo", "Marco"),
            lastNames = listOf("Barella", "Bastoni", "Chiesa", "Donnarumma", "Pellegrini", "Calafiori", "Retegui", "Frattesi", "Tonali", "Cambiaso", "Raspadori", "Rossi")
        ),
        CountryInfo(
            id = "FRA", name = "France", confederation = Confederation.UEFA, fifaRanking = 2,
            domesticCupName = "Coupe de France", superCupName = "Trophée des Champions",
            verifiedDivisions = listOf("Ligue 1", "Ligue 2", "Championnat National", "National 2 Groupe A", "National 2 Groupe B", "National 2 Groupe C", "National 3 Nord", "National 3 Sud"),
            totalTargetDivisions = 9,
            sampleVerifiedClubs = listOf("Paris SG", "Olympique Marseille", "AS Monaco", "Olympique Lyonnais", "LOSC Lille", "OGC Nice", "RC Lens", "Stade Rennais", "Saint-Étienne", "Girondins Bordeaux", "FC Nantes", "RC Strasbourg"),
            firstNames = listOf("Kylian", "Antoine", "William", "Warren", "Bradley", "Ousmane", "Eduardo", "Aurelien", "Mike", "Theo", "Jules", "Rayan"),
            lastNames = listOf("Mbappe", "Griezmann", "Saliba", "Zaire-Emery", "Barcola", "Dembele", "Camavinga", "Tchouameni", "Maignan", "Hernandez", "Kounde", "Cherki")
        ),
        CountryInfo(
            id = "POR", name = "Portugal", confederation = Confederation.UEFA, fifaRanking = 7,
            domesticCupName = "Taça de Portugal", superCupName = "Supertaça Cândido de Oliveira",
            verifiedDivisions = listOf("Primeira Liga", "Liga Portugal 2", "Liga 3 North", "Liga 3 South", "Campeonato de Portugal A", "Campeonato de Portugal B"),
            totalTargetDivisions = 7,
            sampleVerifiedClubs = listOf("SL Benfica", "Sporting CP", "FC Porto", "SC Braga", "Vitória Guimarães", "Boavista FC", "Famalicão", "Gil Vicente", "Marítimo", "Académica"),
            firstNames = listOf("Bruno", "Bernardo", "Rafael", "Ruben", "Diogo", "Joao", "Vitinha", "Goncalo", "Nuno", "Pedro"),
            lastNames = listOf("Fernandes", "Silva", "Leao", "Dias", "Costa", "Neves", "Ramos", "Mendes", "Inacio", "Neto")
        ),
        CountryInfo(
            id = "NED", name = "Netherlands", confederation = Confederation.UEFA, fifaRanking = 8,
            domesticCupName = "KNVB Cup", superCupName = "Johan Cruyff Shield",
            verifiedDivisions = listOf("Eredivisie", "Eerste Divisie", "Tweede Divisie", "Derde Divisie Zaterdag", "Derde Divisie Zondag", "Vierde Divisie"),
            totalTargetDivisions = 6,
            sampleVerifiedClubs = listOf("Ajax Amsterdam", "PSV Eindhoven", "Feyenoord", "AZ Alkmaar", "FC Twente", "FC Utrecht", "SC Heerenveen", "Sparta Rotterdam", "NEC Nijmegen", "Willem II"),
            firstNames = listOf("Virgil", "Cody", "Xavi", "Frenkie", "Tijjani", "Denzel", "Nathan", "Micky", "Jeremie", "Brian"),
            lastNames = listOf("van Dijk", "Gakpo", "Simons", "de Jong", "Reijnders", "Dumfries", "Ake", "van de Ven", "Frimpong", "Brobbey")
        ),
        // AFRICA (CAF) - Including Algeria, Morocco, Tunisia, Egypt, Nigeria, Senegal, South Africa, etc.
        CountryInfo(
            id = "ALG", name = "Algeria", confederation = Confederation.CAF, fifaRanking = 37,
            domesticCupName = "Algerian Cup (Coupe d'Algérie)", superCupName = "Algerian Super Cup",
            verifiedDivisions = listOf(
                "Algerian Ligue Professionnelle 1", "Ligue 2 Centre-Est", "Ligue 2 Centre-Ouest",
                "Inter-Régions Centre", "Inter-Régions Est", "Inter-Régions Ouest",
                "Inter-Régions Sud-Est", "Inter-Régions Sud-Ouest", "Régionale 1 Oran", "Régionale 1 Alger"
            ),
            totalTargetDivisions = 10,
            sampleVerifiedClubs = listOf("MC Oran", "MC Alger", "CR Belouizdad", "USM Alger", "JS Kabylie", "ES Sétif", "CS Constantine", "ASO Chlef", "JS Saoura", "USM El Harrach", "ASM Oran", "NA Hussein Dey", "Paradou AC", "MC El Bayadh", "USM Annaba", "WA Tlemcen"),
            firstNames = listOf("Riyad", "Ismael", "Amine", "Ramy", "Yacine", "Baghdad", "Houssem", "Said", "Rayan", "Mohamed", "Youcef", "Adem"),
            lastNames = listOf("Mahrez", "Bennacer", "Gouiri", "Bensebaini", "Brahimi", "Bounedjah", "Aouar", "Benrahma", "Ait-Nouri", "Amoura", "Atal", "Zorgane")
        ),
        CountryInfo(
            id = "MAR", name = "Morocco", confederation = Confederation.CAF, fifaRanking = 13,
            domesticCupName = "Moroccan Throne Cup", superCupName = "Moroccan Super Cup",
            verifiedDivisions = listOf("Botola Pro 1", "Botola Pro 2", "National Amateur", "Amateurs I Nord", "Amateurs I Sud", "Amateurs II"),
            totalTargetDivisions = 7,
            sampleVerifiedClubs = listOf("Raja Casablanca", "Wydad AC", "AS FAR Rabat", "RS Berkane", "FUS Rabat", "MAS Fès", "Hassania Agadir", "Ittihad Tanger", "Olympique Safi", "KAC Marrakech"),
            firstNames = listOf("Achraf", "Brahim", "Hakim", "Yassine", "Sofyan", "Noussair", "Youssef", "Bilal", "Azzedine", "Soufiane"),
            lastNames = listOf("Hakimi", "Diaz", "Ziyech", "Bounou", "Amrabat", "Mazraoui", "En-Nesyri", "El Khannouss", "Ounahi", "Rahimi")
        ),
        CountryInfo(
            id = "TUN", name = "Tunisia", confederation = Confederation.CAF, fifaRanking = 41,
            domesticCupName = "Tunisian Cup", superCupName = "Tunisian Super Cup",
            verifiedDivisions = listOf("Ligue Professionnelle 1", "Ligue 2 Poule A", "Ligue 2 Poule B", "Ligue 3 Nord", "Ligue 3 Sud", "Ligue 4 Amateur"),
            totalTargetDivisions = 6,
            sampleVerifiedClubs = listOf("Espérance de Tunis", "Club Africain", "Étoile du Sahel", "CS Sfaxien", "US Monastir", "Stade Tunisien", "CA Bizertin", "Olympique Béja"),
            firstNames = listOf("Ellyes", "Youssef", "Montassar", "Hannibal", "Aissa", "Ali", "Mohamed", "Seifeddine"),
            lastNames = listOf("Skhiri", "Msakni", "Talbi", "Mejbri", "Laidouni", "Abdi", "Ben Romdhane", "Jaziri")
        ),
        CountryInfo(
            id = "EGY", name = "Egypt", confederation = Confederation.CAF, fifaRanking = 30,
            domesticCupName = "Egypt Cup", superCupName = "Egyptian Super Cup",
            verifiedDivisions = listOf("Egyptian Premier League", "Egyptian Second Division A", "Second Division B North", "Second Division B South", "Third Division Cairo", "Third Division Alexandria"),
            totalTargetDivisions = 7,
            sampleVerifiedClubs = listOf("Al Ahly SC", "Zamalek SC", "Pyramids FC", "Al Masry", "Ismaily SC", "Al Ittihad Alexandria", "ENPPI", "Smouha SC", "Ceramica Cleopatra", "Ghazl El Mahalla"),
            firstNames = listOf("Mohamed", "Omar", "Mostafa", "Mahmoud", "Ahmed", "Emam", "Ramy", "Ibrahim"),
            lastNames = listOf("Salah", "Marmoush", "Mohamed", "Trezeguet", "Zizo", "Ashour", "Rabia", "Adel")
        ),
        // SOUTH AMERICA (CONMEBOL)
        CountryInfo(
            id = "BRA", name = "Brazil", confederation = Confederation.CONMEBOL, fifaRanking = 5,
            domesticCupName = "Copa do Brasil", superCupName = "Supercopa do Brasil",
            verifiedDivisions = listOf("Brasileirão Série A", "Brasileirão Série B", "Brasileirão Série C", "Série D Grupo A", "Série D Grupo B", "Série D Grupo C", "Série D Grupo D", "Paulistão Regional", "Carioca Regional", "Mineiro Regional"),
            totalTargetDivisions = 10,
            sampleVerifiedClubs = listOf("Flamengo", "Palmeiras", "São Paulo FC", "Corinthians", "Fluminense", "Botafogo", "Grêmio", "Internacional", "Atlético Mineiro", "Cruzeiro", "Santos FC", "Vasco da Gama", "Bahia", "Fortaleza"),
            firstNames = listOf("Vinicius", "Rodrygo", "Endrick", "Bruno", "Alisson", "Marquinhos", "Gabriel", "Raphinha", "Savinho", "Estevao"),
            lastNames = listOf("Junior", "Goes", "Guimaraes", "Becker", "Magalhaes", "Martinelli", "Paqueta", "Silva", "Oliveira", "Santos")
        ),
        CountryInfo(
            id = "ARG", name = "Argentina", confederation = Confederation.CONMEBOL, fifaRanking = 1,
            domesticCupName = "Copa Argentina", superCupName = "Supercopa Argentina",
            verifiedDivisions = listOf("Liga Profesional", "Primera Nacional Zona A", "Primera Nacional Zona B", "Primera B Metropolitana", "Torneo Federal A", "Primera C", "Torneo Regional Amateur"),
            totalTargetDivisions = 8,
            sampleVerifiedClubs = listOf("River Plate", "Boca Juniors", "Racing Club", "Independiente", "San Lorenzo", "Vélez Sarsfield", "Estudiantes LP", "Talleres", "Rosario Central", "Newell's Old Boys", "Lanús", "Argentinos Juniors"),
            firstNames = listOf("Lionel", "Julian", "Lautaro", "Alexis", "Enzo", "Emiliano", "Cristian", "Rodrigo", "Alejandro", "Thiago"),
            lastNames = listOf("Messi", "Alvarez", "Martinez", "Mac Allister", "Fernandez", "Romero", "De Paul", "Garnacho", "Almada", "Paz")
        ),
        // NORTH AMERICA (CONCACAF)
        CountryInfo(
            id = "USA", name = "United States", confederation = Confederation.CONCACAF, fifaRanking = 16,
            domesticCupName = "U.S. Open Cup", superCupName = "MLS Campeones Shield",
            verifiedDivisions = listOf("Major League Soccer East", "Major League Soccer West", "USL Championship East", "USL Championship West", "MLS Next Pro", "USL League One", "NISA Pro", "USL League Two"),
            totalTargetDivisions = 8,
            sampleVerifiedClubs = listOf("Inter Miami", "LA Galaxy", "LAFC", "Seattle Sounders", "Columbus Crew", "FC Cincinnati", "Atlanta United", "New York Red Bulls", "NYCFC", "Philadelphia Union", "Portland Timbers", "Detroit City FC"),
            firstNames = listOf("Christian", "Weston", "Giovanni", "Folarin", "Antonee", "Tyler", "Yunus", "Ricardo", "Timothy", "Brenden"),
            lastNames = listOf("Pulisic", "McKennie", "Reyna", "Balogun", "Robinson", "Adams", "Musah", "Pepi", "Weah", "Aaronson")
        ),
        CountryInfo(
            id = "MEX", name = "Mexico", confederation = Confederation.CONCACAF, fifaRanking = 17,
            domesticCupName = "Copa MX", superCupName = "Campeón de Campeones",
            verifiedDivisions = listOf("Liga MX", "Liga de Expansión MX", "Liga Premier Serie A", "Liga Premier Serie B", "Liga TDP Norte", "Liga TDP Sur"),
            totalTargetDivisions = 6,
            sampleVerifiedClubs = listOf("Club América", "Chivas Guadalajara", "Cruz Azul", "Tigres UANL", "CF Monterrey", "Pumas UNAM", "Toluca", "Pachuca", "Santos Laguna", "León"),
            firstNames = listOf("Santiago", "Edson", "Hirving", "Johan", "Cesar", "Luis", "Orbelin", "Raul", "Julian", "Marcel"),
            lastNames = listOf("Gimenez", "Alvarez", "Lozano", "Vasquez", "Montes", "Chavez", "Pineda", "Jimenez", "Quinones", "Ruiz")
        ),
        // ASIA (AFC)
        CountryInfo(
            id = "JPN", name = "Japan", confederation = Confederation.AFC, fifaRanking = 15,
            domesticCupName = "Emperor's Cup", superCupName = "Japanese Super Cup",
            verifiedDivisions = listOf("J1 League", "J2 League", "J3 League", "Japan Football League (JFL)", "Kantō Soccer League", "Kansai Soccer League", "Tōkai Regional League"),
            totalTargetDivisions = 7,
            sampleVerifiedClubs = listOf("Vissel Kobe", "Yokohama F. Marinos", "Kawasaki Frontale", "Urawa Red Diamonds", "Kashima Antlers", "Sanfrecce Hiroshima", "Gamba Osaka", "FC Tokyo", "Nagoya Grampus", "Cerezo Osaka"),
            firstNames = listOf("Takefusa", "Kaoru", "Wataru", "Takumi", "Daichi", "Ritsu", "Takehiro", "Ko", "Zion", "Ayase"),
            lastNames = listOf("Kubo", "Mitoma", "Endo", "Minamino", "Kamada", "Doan", "Tomiyasu", "Itakura", "Suzuki", "Ueda")
        ),
        CountryInfo(
            id = "KSA", name = "Saudi Arabia", confederation = Confederation.AFC, fifaRanking = 56,
            domesticCupName = "King's Cup", superCupName = "Saudi Super Cup",
            verifiedDivisions = listOf("Saudi Pro League", "Saudi First Division League", "Second Division Group A", "Second Division Group B", "Third Division Kingdom"),
            totalTargetDivisions = 6,
            sampleVerifiedClubs = listOf("Al-Hilal", "Al-Nassr", "Al-Ittihad", "Al-Ahli", "Al-Shabab", "Al-Ettifaq", "Al-Taawoun", "Al-Fateh", "Al-Qadsiah", "Damac FC"),
            firstNames = listOf("Salem", "Saud", "Firas", "Hassan", "Ali", "Mohamed", "Sultan", "Abdulelah"),
            lastNames = listOf("Al-Dawsari", "Abdulhamid", "Al-Buraikan", "Al-Tambakti", "Lajami", "Kanno", "Al-Ghannam", "Al-Malki")
        )
    )

    // Additional 48 Global Football Associations to exceed 415+ Total Playable Divisions Worldwide
    private val EXTRA_NATIONS: List<Triple<String, String, Confederation>> = listOf(
        Triple("SCO", "Scotland", Confederation.UEFA),
        Triple("WAL", "Wales", Confederation.UEFA),
        Triple("NIR", "Northern Ireland", Confederation.UEFA),
        Triple("BEL", "Belgium", Confederation.UEFA),
        Triple("AUT", "Austria", Confederation.UEFA),
        Triple("SUI", "Switzerland", Confederation.UEFA),
        Triple("TUR", "Turkey", Confederation.UEFA),
        Triple("GRE", "Greece", Confederation.UEFA),
        Triple("DEN", "Denmark", Confederation.UEFA),
        Triple("SWE", "Sweden", Confederation.UEFA),
        Triple("NOR", "Norway", Confederation.UEFA),
        Triple("FIN", "Finland", Confederation.UEFA),
        Triple("POL", "Poland", Confederation.UEFA),
        Triple("CZE", "Czechia", Confederation.UEFA),
        Triple("CRO", "Croatia", Confederation.UEFA),
        Triple("SRB", "Serbia", Confederation.UEFA),
        Triple("ROU", "Romania", Confederation.UEFA),
        Triple("HUN", "Hungary", Confederation.UEFA),
        Triple("UKR", "Ukraine", Confederation.UEFA),
        Triple("RUS", "Russia", Confederation.UEFA),
        Triple("RSA", "South Africa", Confederation.CAF),
        Triple("NGA", "Nigeria", Confederation.CAF),
        Triple("SEN", "Senegal", Confederation.CAF),
        Triple("GHA", "Ghana", Confederation.CAF),
        Triple("CMR", "Cameroon", Confederation.CAF),
        Triple("CIV", "Ivory Coast", Confederation.CAF),
        Triple("MLI", "Mali", Confederation.CAF),
        Triple("COD", "DR Congo", Confederation.CAF),
        Triple("URU", "Uruguay", Confederation.CONMEBOL),
        Triple("CHI", "Chile", Confederation.CONMEBOL),
        Triple("COL", "Colombia", Confederation.CONMEBOL),
        Triple("ECU", "Ecuador", Confederation.CONMEBOL),
        Triple("PER", "Peru", Confederation.CONMEBOL),
        Triple("PAR", "Paraguay", Confederation.CONMEBOL),
        Triple("BOL", "Bolivia", Confederation.CONMEBOL),
        Triple("CAN", "Canada", Confederation.CONCACAF),
        Triple("CRC", "Costa Rica", Confederation.CONCACAF),
        Triple("JAM", "Jamaica", Confederation.CONCACAF),
        Triple("KOR", "South Korea", Confederation.AFC),
        Triple("CHN", "China", Confederation.AFC),
        Triple("AUS", "Australia", Confederation.AFC),
        Triple("QAT", "Qatar", Confederation.AFC),
        Triple("UAE", "United Arab Emirates", Confederation.AFC),
        Triple("IND", "India", Confederation.AFC),
        Triple("IDN", "Indonesia", Confederation.AFC),
        Triple("MYS", "Malaysia", Confederation.AFC),
        Triple("THA", "Thailand", Confederation.AFC),
        Triple("VNM", "Vietnam", Confederation.AFC),
        Triple("NZL", "New Zealand", Confederation.OFC),
        Triple("FIJ", "Fiji", Confederation.OFC)
    )

    fun getAllCountries(): List<CountryInfo> {
        val extras = EXTRA_NATIONS.mapIndexed { idx, (code, name, conf) ->
            CountryInfo(
                id = code,
                name = name,
                confederation = conf,
                fifaRanking = 20 + idx,
                domesticCupName = "$name National FA Cup",
                superCupName = "$name Super Cup",
                verifiedDivisions = listOf(
                    "$name Premier Division",
                    "$name Second Division",
                    "$name Third Division",
                    "$name Regional North",
                    "$name Regional South",
                    "$name Amateur League 1",
                    "$name Amateur League 2"
                ),
                totalTargetDivisions = 7,
                sampleVerifiedClubs = listOf(
                    "$name Capital FC", "$name United", "$name Athletic", "$name Rovers",
                    "$name Sporting", "$name City", "$name Dynamo", "$name Olympic"
                ),
                firstNames = listOf("Alex", "Luka", "Mateo", "Karim", "Samuel", "David", "Milan", "Kevin", "Victor", "osc"),
                lastNames = listOf("Kovac", "novak", "Silva", "Mensah", "Diallo", "Kim", "Petrov", "Jensen", "Lind", "Tan")
            )
        }
        return COUNTRIES + extras
    }
}

// =========================================================================
// 3. NORMALIZED SQLITE DATABASE ENGINE (WITH PAGINATION, BACKUP, JSON/CSV)
// =========================================================================

class WfmDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "world_football_manager.db"
        const val DB_VERSION = 2

        @Volatile
        private var instance: WfmDatabase? = null

        fun getInstance(context: Context): WfmDatabase {
            return instance ?: synchronized(this) {
                instance ?: WfmDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE divisions (
                id TEXT PRIMARY KEY,
                countryId TEXT,
                name TEXT,
                tier INTEGER,
                groupIndex INTEGER,
                teamCount INTEGER,
                promotionSpots INTEGER,
                playoffSpots INTEGER,
                relegationSpots INTEGER,
                pointsForWin INTEGER,
                prizePool INTEGER,
                isOfficialStructure INTEGER,
                isActiveSim INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE clubs (
                id TEXT PRIMARY KEY,
                name TEXT,
                shortName TEXT,
                countryId TEXT,
                divisionId TEXT,
                city TEXT,
                primaryColorHex INTEGER,
                secondaryColorHex INTEGER,
                reputation INTEGER,
                balance INTEGER,
                transferBudget INTEGER,
                wageBudgetWeekly INTEGER,
                debt INTEGER,
                stadiumName TEXT,
                stadiumCapacity INTEGER,
                ticketPrice INTEGER,
                trainingFacilityLevel INTEGER,
                youthFacilityLevel INTEGER,
                medicalFacilityLevel INTEGER,
                scoutingNetworkLevel INTEGER,
                philosophy TEXT,
                rivalClubId TEXT,
                isFictional INTEGER,
                managerName TEXT,
                formation TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE players (
                id TEXT PRIMARY KEY,
                fullName TEXT,
                birthYear INTEGER,
                age INTEGER,
                nationality TEXT,
                secondNationality TEXT,
                position TEXT,
                secondaryPosition TEXT,
                preferredFoot TEXT,
                heightCm INTEGER,
                weightKg INTEGER,
                clubId TEXT,
                squadNumber INTEGER,
                wageWeekly INTEGER,
                contractExpiryYear INTEGER,
                releaseClause INTEGER,
                marketValue INTEGER,
                morale INTEGER,
                fitness INTEGER,
                sharpness INTEGER,
                reputation INTEGER,
                personality TEXT,
                injuryWeeks INTEGER,
                seasonGoals INTEGER,
                seasonAssists INTEGER,
                seasonApps INTEGER,
                careerGoals INTEGER,
                intlCaps INTEGER,
                intlGoals INTEGER,
                currentAbility INTEGER,
                potentialAbility INTEGER,
                isYouthAcademy INTEGER,
                isTransferListed INTEGER,
                isLoanListed INTEGER,
                loanedFromClubId TEXT,
                isFictional INTEGER,
                attributesJson TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE standings (
                clubId TEXT,
                clubName TEXT,
                divisionId TEXT,
                played INTEGER,
                won INTEGER,
                drawn INTEGER,
                lost INTEGER,
                goalsFor INTEGER,
                goalsAgainst INTEGER,
                points INTEGER,
                PRIMARY KEY (clubId, divisionId)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE fixtures (
                id TEXT PRIMARY KEY,
                competitionId TEXT,
                competitionName TEXT,
                isCup INTEGER,
                roundNumber INTEGER,
                weekNumber INTEGER,
                homeClubId TEXT,
                homeClubName TEXT,
                awayClubId TEXT,
                awayClubName TEXT,
                played INTEGER,
                homeGoals INTEGER,
                awayGoals INTEGER,
                penaltyWinnerId TEXT,
                summaryText TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE inbox (
                id TEXT PRIMARY KEY,
                week INTEGER,
                seasonYear INTEGER,
                category TEXT,
                title TEXT,
                body TEXT,
                isRead INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE history_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seasonYear INTEGER,
                competitionId TEXT,
                competitionName TEXT,
                winnerClubName TEXT,
                runnerUpClubName TEXT,
                topScorerName TEXT,
                topScorerGoals INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE save_slots (
                slotId INTEGER PRIMARY KEY,
                saveName TEXT,
                seasonYear INTEGER,
                currentWeek INTEGER,
                managerJson TEXT,
                tacticsJson TEXT,
                shortlistIds TEXT,
                updatedAt TEXT
            )
        """.trimIndent())

        // High-Speed Indexes for 400+ Divisions & Tens of Thousands of Players
        db.execSQL("CREATE INDEX idx_clubs_div ON clubs(divisionId)")
        db.execSQL("CREATE INDEX idx_players_club ON players(clubId)")
        db.execSQL("CREATE INDEX idx_players_ca ON players(currentAbility DESC)")
        db.execSQL("CREATE INDEX idx_fixtures_week ON fixtures(weekNumber, competitionId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < newVersion) {
            // Safe schema migration preserving saves
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_players_ca ON players(currentAbility DESC)")
        }
    }

    // ---------------------------------------------------------------------
    // WORLD SEEDING: GENERATES 420+ DIVISIONS WORLDWIDE ON FIRST LAUNCH
    // ---------------------------------------------------------------------
    fun ensureWorldSeeded() {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM divisions", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        if (count > 0) return

        val rng = Random(20261024L)
        val allCountries = WorldFootballCatalog.getAllCountries()

        db.beginTransaction()
        try {
            var divisionCounter = 0
            for (country in allCountries) {
                val divNames = country.verifiedDivisions.toMutableList()
                while (divNames.size < country.totalTargetDivisions) {
                    val nextTier = divNames.size + 1
                    divNames.add("${country.name} Regional Division $nextTier (Fictional Lower Tier)")
                }

                divNames.forEachIndexed { idx, divName ->
                    divisionCounter++
                    val tier = idx + 1
                    val divId = "${country.id}_D$tier"
                    val isOfficial = idx < country.verifiedDivisions.size
                    val isActiveDefault = (country.id in listOf("ENG", "ESP", "GER", "ITA", "FRA", "ALG", "BRA", "ARG") && tier <= 2)
                    val teamCount = if (tier <= 2) 16 else 12
                    val prize = max(50_000L, (25_000_000L / (tier * tier)))

                    val divValues = ContentValues().apply {
                        put("id", divId)
                        put("countryId", country.id)
                        put("name", divName)
                        put("tier", tier)
                        put("groupIndex", 1)
                        put("teamCount", teamCount)
                        put("promotionSpots", if (tier == 1) 0 else 2)
                        put("playoffSpots", if (tier == 1) 0 else 4)
                        put("relegationSpots", if (tier == divNames.size) 0 else 2)
                        put("pointsForWin", 3)
                        put("prizePool", prize)
                        put("isOfficialStructure", if (isOfficial) 1 else 0)
                        put("isActiveSim", if (isActiveDefault) 1 else 0)
                    }
                    db.insert("divisions", null, divValues)

                    // Generate Clubs for Tier 1 & Tier 2 immediately, and top clubs for lower tiers
                    // (Lower inactive tiers generate full squads dynamically when activated or queried)
                    val clubsToCreate = if (tier <= 2) teamCount else 6
                    for (cIdx in 0 until clubsToCreate) {
                        val clubId = "${divId}_C${cIdx + 1}"
                        val isVerifiedName = (tier == 1 && cIdx < country.sampleVerifiedClubs.size)
                        val clubName = if (isVerifiedName) {
                            country.sampleVerifiedClubs[cIdx]
                        } else if (tier == 2 && (cIdx + teamCount) < country.sampleVerifiedClubs.size) {
                            country.sampleVerifiedClubs[cIdx + teamCount]
                        } else {
                            val suffixes = listOf("FC", "United", "Athletic", "Sporting", "SC", "Wanderers", "City", "Olympic")
                            "${country.name} ${divName.take(10).trim()} ${suffixes[cIdx % suffixes.size]} ${cIdx + 1}"
                        }
                        val rep = (92 - (tier - 1) * 11 - (cIdx * 2) - (country.fifaRanking / 4)).coerceIn(15, 95)
                        val balance = (rep * rep * 85_000L)
                        val transferBudget = balance / 3
                        val wageBudget = max(15_000L, balance / 40)

                        val cValues = ContentValues().apply {
                            put("id", clubId)
                            put("name", clubName)
                            put("shortName", clubName.take(12))
                            put("countryId", country.id)
                            put("divisionId", divId)
                            put("city", "${country.name} Metro ${cIdx + 1}")
                            put("primaryColorHex", listOf(0xFFD32F2FL, 0xFF1976D2L, 0xFF388E3CL, 0xFFFBC02DL, 0xFF7B1FA2L, 0xFF00796BL)[cIdx % 6])
                            put("secondaryColorHex", 0xFFFFFFFFL)
                            put("reputation", rep)
                            put("balance", balance)
                            put("transferBudget", transferBudget)
                            put("wageBudgetWeekly", wageBudget)
                            put("debt", if (rng.nextInt(100) < 20) balance / 4 else 0L)
                            put("stadiumName", "$clubName Arena")
                            put("stadiumCapacity", (rep * 650).coerceIn(2500, 85000))
                            put("ticketPrice", (12 + rep / 3))
                            put("trainingFacilityLevel", (rep / 5).coerceIn(1, 20))
                            put("youthFacilityLevel", (rep / 5).coerceIn(1, 20))
                            put("medicalFacilityLevel", (rep / 5).coerceIn(1, 20))
                            put("scoutingNetworkLevel", (rep / 5).coerceIn(1, 20))
                            put("philosophy", listOf("Balanced", "Youth Development", "Attacking Mastery", "Financial Sustainability")[cIdx % 4])
                            put("rivalClubId", "${divId}_C${((cIdx + 1) % clubsToCreate) + 1}")
                            put("isFictional", if (isVerifiedName) 0 else 1)
                            put("managerName", "${country.firstNames.random(rng)} ${country.lastNames.random(rng)}")
                            put("formation", listOf("4-3-3", "4-2-3-1", "4-4-2", "3-5-2")[cIdx % 4])
                        }
                        db.insert("clubs", null, cValues)

                        // Insert initial standings row
                        val sValues = ContentValues().apply {
                            put("clubId", clubId)
                            put("clubName", clubName)
                            put("divisionId", divId)
                            put("played", 0); put("won", 0); put("drawn", 0); put("lost", 0)
                            put("goalsFor", 0); put("goalsAgainst", 0); put("points", 0)
                        }
                        db.insert("standings", null, sValues)

                        // Seed 20 players for active clubs, or core squad for background clubs
                        if (isActiveDefault || (tier == 1 && cIdx < 4)) {
                            seedSquadForClub(db, clubId, country, rep, rng)
                        }
                    }

                    if (isActiveDefault) {
                        generateFixturesForDivision(db, divId, divName)
                    }
                }
            }

            // Add initial welcome inbox messages
            insertInboxMessage(
                db, InboxMessage(
                    id = "MSG_WELCOME_1",
                    week = 1,
                    seasonYear = 2026,
                    category = "Board",
                    title = "Welcome to World Football Manager (420+ Divisions)",
                    body = "The board welcomes you to the club. Our database is initialized with ${divisionCounter} divisions across 67 football associations worldwide. Review your squad, customize your tactics, and prepare for Matchday 1!"
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun seedSquadForClub(db: SQLiteDatabase, clubId: String, country: CountryInfo, clubRep: Int, rng: Random) {
        val positions = listOf(
            "GK", "GK",
            "LB", "CB", "CB", "CB", "RB",
            "DM", "CM", "CM", "CM", "AM",
            "LW", "RW", "ST", "ST",
            "CB", "CM", "LW", "ST" // 4 Youth Academy prospects
        )
        positions.forEachIndexed { idx, pos ->
            val isYouth = idx >= 16
            val age = if (isYouth) rng.nextInt(16, 19) else rng.nextInt(19, 34)
            val birthYear = 2026 - age
            val baseSkill = (clubRep - (if (isYouth) 14 else 0) + rng.nextInt(-6, 8)).coerceIn(32, 92)
            val attrs = generateAttributesForPosition(pos, baseSkill, rng)
            val ca = attrs.calculateRoleOverall(pos)
            val pa = max(ca + 2, (ca + (if (age < 23) rng.nextInt(6, 22) else rng.nextInt(0, 5)))).coerceIn(ca, 99)
            val value = max(25_000L, (ca.toLong() * ca.toLong() * ca.toLong() * 45L))
            val wage = max(500L, value / 220L)

            val pId = "${clubId}_P${idx + 1}_${rng.nextInt(1000, 9999)}"
            val fullName = "${country.firstNames.random(rng)} ${country.lastNames.random(rng)}"

            val pValues = ContentValues().apply {
                put("id", pId)
                put("fullName", fullName)
                put("birthYear", birthYear)
                put("age", age)
                put("nationality", country.id)
                put("secondNationality", "")
                put("position", pos)
                put("secondaryPosition", if (pos == "CB") "DM" else if (pos == "LW") "RW" else "CM")
                put("preferredFoot", if (pos == "LB" || pos == "LW") "Left" else if (rng.nextInt(10) < 2) "Both" else "Right")
                put("heightCm", if (pos == "GK" || pos == "CB") rng.nextInt(185, 200) else rng.nextInt(170, 189))
                put("weightKg", rng.nextInt(66, 88))
                put("clubId", clubId)
                put("squadNumber", idx + 1)
                put("wageWeekly", wage)
                put("contractExpiryYear", 2026 + rng.nextInt(1, 5))
                put("releaseClause", value * 2)
                put("marketValue", value)
                put("morale", rng.nextInt(72, 95))
                put("fitness", rng.nextInt(88, 100))
                put("sharpness", rng.nextInt(75, 95))
                put("reputation", ca)
                put("personality", listOf("Professional", "Driven", "Ambitious", "Model Citizen", "Balanced", "Loyal")[idx % 6])
                put("injuryWeeks", 0)
                put("seasonGoals", 0)
                put("seasonAssists", 0)
                put("seasonApps", 0)
                put("careerGoals", if (pos in listOf("ST", "LW", "RW")) (age - 18) * rng.nextInt(4, 14) else (age - 18) * rng.nextInt(0, 3))
                put("intlCaps", if (ca > 78) rng.nextInt(5, 65) else 0)
                put("intlGoals", if (ca > 78 && pos == "ST") rng.nextInt(2, 28) else 0)
                put("currentAbility", ca)
                put("potentialAbility", pa)
                put("isYouthAcademy", if (isYouth) 1 else 0)
                put("isTransferListed", if (idx == 15 && rng.nextBoolean()) 1 else 0)
                put("isLoanListed", if (isYouth && rng.nextBoolean()) 1 else 0)
                put("loanedFromClubId", "")
                put("isFictional", 1)
                put("attributesJson", attrs.toJson().toString())
            }
            db.insert("players", null, pValues)
        }
    }

    fun generateAttributesForPosition(pos: String, base: Int, rng: Random): PlayerAttributes {
        fun r(boost: Int = 0): Int = (base + boost + rng.nextInt(-8, 9)).coerceIn(15, 98)
        val isGk = pos == "GK"
        val isDef = pos in listOf("CB", "LB", "RB", "DM")
        val isMid = pos in listOf("DM", "CM", "AM")
        val isAtt = pos in listOf("LW", "RW", "ST", "AM")

        return PlayerAttributes(
            passing = if (isMid) r(8) else r(0),
            finishing = if (pos == "ST") r(12) else if (isAtt) r(6) else r(-15),
            dribbling = if (isAtt) r(8) else r(-4),
            crossing = if (pos in listOf("LB", "RB", "LW", "RW")) r(10) else r(-6),
            firstTouch = if (isMid || isAtt) r(6) else r(0),
            tackling = if (isDef) r(11) else r(-12),
            marking = if (isDef) r(11) else r(-12),
            heading = if (pos in listOf("CB", "ST")) r(9) else r(-4),
            longShots = if (isMid || isAtt) r(5) else r(-10),
            technique = r(3),
            setPieces = r(0),
            penalties = r(2),
            decisions = r(4),
            vision = if (isMid) r(9) else r(-2),
            composure = r(3),
            anticipation = r(3),
            concentration = r(2),
            positioning = if (isDef) r(9) else r(0),
            teamwork = r(5),
            leadership = r(0),
            aggression = r(0),
            workRate = r(5),
            determination = r(6),
            offTheBall = if (isAtt) r(9) else r(-2),
            acceleration = if (pos in listOf("LW", "RW", "LB", "RB", "ST")) r(8) else r(0),
            pace = if (pos in listOf("LW", "RW", "LB", "RB", "ST")) r(8) else r(0),
            stamina = r(5),
            strength = if (pos in listOf("CB", "ST", "DM")) r(8) else r(0),
            agility = r(3),
            balance = r(3),
            jumping = if (pos in listOf("CB", "GK", "ST")) r(8) else r(0),
            naturalFitness = r(6),
            reflexes = if (isGk) r(12) else 15,
            handling = if (isGk) r(11) else 15,
            gkPositioning = if (isGk) r(11) else 15,
            aerialAbility = if (isGk) r(9) else 15,
            distribution = if (isGk) r(6) else 15,
            oneOnOnes = if (isGk) r(10) else 15
        )
    }

    fun generateFixturesForDivision(db: SQLiteDatabase, divisionId: String, divisionName: String) {
        val clubIds = mutableListOf<Pair<String, String>>()
        val c = db.rawQuery("SELECT id, name FROM clubs WHERE divisionId = ?", arrayOf(divisionId))
        while (c.moveToNext()) {
            clubIds.add(c.getString(0) to c.getString(1))
        }
        c.close()
        if (clubIds.size < 2) return

        // Round-robin schedule (Home & Away)
        val n = clubIds.size
        val rounds = (n - 1) * 2
        val half = n / 2
        val rotating = clubIds.drop(1).toMutableList()

        for (round in 1..rounds) {
            val isSecondHalf = round > (n - 1)
            for (i in 0 until half) {
                val teamA = if (i == 0) clubIds[0] else rotating[i - 1]
                val teamB = rotating[rotating.size - 1 - i]
                val (home, away) = if (!isSecondHalf) teamA to teamB else teamB to teamA
                val fValues = ContentValues().apply {
                    put("id", "${divisionId}_R${round}_M$i")
                    put("competitionId", divisionId)
                    put("competitionName", divisionName)
                    put("isCup", 0)
                    put("roundNumber", round)
                    put("weekNumber", round)
                    put("homeClubId", home.first)
                    put("homeClubName", home.second)
                    put("awayClubId", away.first)
                    put("awayClubName", away.second)
                    put("played", 0)
                    put("homeGoals", 0)
                    put("awayGoals", 0)
                    put("penaltyWinnerId", "")
                    put("summaryText", "")
                }
                db.insertWithOnConflict("fixtures", null, fValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val last = rotating.removeAt(rotating.size - 1)
            rotating.add(0, last)
        }
    }

    fun insertInboxMessage(db: SQLiteDatabase, msg: InboxMessage) {
        val values = ContentValues().apply {
            put("id", msg.id)
            put("week", msg.week)
            put("seasonYear", msg.seasonYear)
            put("category", msg.category)
            put("title", msg.title)
            put("body", msg.body)
            put("isRead", if (msg.isRead) 1 else 0)
        }
        db.insertWithOnConflict("inbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------------------------------------------------------------------
    // QUERIES & CRUD FOR UI, SIMULATION, AND IN-GAME EDITOR
    // ---------------------------------------------------------------------
    fun getAllDivisions(countryFilter: String = ""): List<DivisionEntity> {
        val list = mutableListOf<DivisionEntity>()
        val db = readableDatabase
        val cursor = if (countryFilter.isBlank()) {
            db.rawQuery("SELECT * FROM divisions ORDER BY countryId ASC, tier ASC", null)
        } else {
            db.rawQuery("SELECT * FROM divisions WHERE countryId = ? ORDER BY tier ASC", arrayOf(countryFilter))
        }
        while (cursor.moveToNext()) {
            list.add(
                DivisionEntity(
                    id = cursor.getString(0),
                    countryId = cursor.getString(1),
                    name = cursor.getString(2),
                    tier = cursor.getInt(3),
                    groupIndex = cursor.getInt(4),
                    teamCount = cursor.getInt(5),
                    promotionSpots = cursor.getInt(6),
                    playoffSpots = cursor.getInt(7),
                    relegationSpots = cursor.getInt(8),
                    pointsForWin = cursor.getInt(9),
                    prizePool = cursor.getLong(10),
                    isOfficialStructure = cursor.getInt(11) == 1,
                    isActiveSim = cursor.getInt(12) == 1
                )
            )
        }
        cursor.close()
        return list
    }

    fun getClubsByDivision(divisionId: String): List<ClubEntity> {
        val list = mutableListOf<ClubEntity>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM clubs WHERE divisionId = ? ORDER BY reputation DESC", arrayOf(divisionId))
        while (cursor.moveToNext()) {
            list.add(cursorToClub(cursor))
        }
        cursor.close()
        return list
    }

    fun getClubById(clubId: String): ClubEntity? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM clubs WHERE id = ?", arrayOf(clubId))
        val club = if (cursor.moveToFirst()) cursorToClub(cursor) else null
        cursor.close()
        return club
    }

    fun searchClubs(query: String, limit: Int = 50): List<ClubEntity> {
        val list = mutableListOf<ClubEntity>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM clubs WHERE name LIKE ? OR countryId LIKE ? ORDER BY reputation DESC LIMIT ?",
            arrayOf("%$query%", "%$query%", limit.toString())
        )
        while (cursor.moveToNext()) list.add(cursorToClub(cursor))
        cursor.close()
        return list
    }

    private fun cursorToClub(cursor: android.database.Cursor): ClubEntity = ClubEntity(
        id = cursor.getString(0),
        name = cursor.getString(1),
        shortName = cursor.getString(2),
        countryId = cursor.getString(3),
        divisionId = cursor.getString(4),
        city = cursor.getString(5),
        primaryColorHex = cursor.getLong(6),
        secondaryColorHex = cursor.getLong(7),
        reputation = cursor.getInt(8),
        balance = cursor.getLong(9),
        transferBudget = cursor.getLong(10),
        wageBudgetWeekly = cursor.getLong(11),
        debt = cursor.getLong(12),
        stadiumName = cursor.getString(13),
        stadiumCapacity = cursor.getInt(14),
        ticketPrice = cursor.getInt(15),
        trainingFacilityLevel = cursor.getInt(16),
        youthFacilityLevel = cursor.getInt(17),
        medicalFacilityLevel = cursor.getInt(18),
        scoutingNetworkLevel = cursor.getInt(19),
        philosophy = cursor.getString(20),
        rivalClubId = cursor.getString(21),
        isFictional = cursor.getInt(22) == 1,
        managerName = cursor.getString(23),
        formation = cursor.getString(24)
    )

    fun getPlayersByClub(clubId: String): List<PlayerEntity> {
        val db = writableDatabase
        val list = mutableListOf<PlayerEntity>()
        val cursor = db.rawQuery("SELECT * FROM players WHERE clubId = ? ORDER BY currentAbility DESC", arrayOf(clubId))
        while (cursor.moveToNext()) {
            list.add(cursorToPlayer(cursor))
        }
        cursor.close()

        // If a lower-division club was selected and hasn't had its squad hydrated yet, hydrate on demand!
        if (list.isEmpty()) {
            val club = getClubById(clubId)
            if (club != null) {
                val country = WorldFootballCatalog.getAllCountries().find { it.id == club.countryId }
                    ?: WorldFootballCatalog.COUNTRIES.first()
                seedSquadForClub(db, clubId, country, club.reputation, Random(clubId.hashCode()))
                return getPlayersByClub(clubId)
            }
        }
        return list
    }

    fun searchPlayers(
        nameQuery: String = "",
        positionFilter: String = "ALL",
        maxAge: Int = 45,
        minOverall: Int = 1,
        transferListedOnly: Boolean = false,
        limit: Int = 80
    ): List<PlayerEntity> {
        val list = mutableListOf<PlayerEntity>()
        val args = mutableListOf<String>()
        val where = StringBuilder("WHERE age <= ? AND currentAbility >= ?")
        args.add(maxAge.toString())
        args.add(minOverall.toString())

        if (nameQuery.isNotBlank()) {
            where.append(" AND (fullName LIKE ? OR nationality LIKE ?)")
            args.add("%$nameQuery%")
            args.add("%$nameQuery%")
        }
        if (positionFilter != "ALL") {
            where.append(" AND position = ?")
            args.add(positionFilter)
        }
        if (transferListedOnly) {
            where.append(" AND isTransferListed = 1")
        }
        args.add(limit.toString())

        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM players $where ORDER BY currentAbility DESC LIMIT ?",
            args.toTypedArray()
        )
        while (cursor.moveToNext()) {
            list.add(cursorToPlayer(cursor))
        }
        cursor.close()
        return list
    }

    private fun cursorToPlayer(c: android.database.Cursor): PlayerEntity {
        val attrObj = try { JSONObject(c.getString(36)) } catch (e: Exception) { JSONObject() }
        return PlayerEntity(
            id = c.getString(0),
            fullName = c.getString(1),
            birthYear = c.getInt(2),
            age = c.getInt(3),
            nationality = c.getString(4),
            secondNationality = c.getString(5),
            position = c.getString(6),
            secondaryPosition = c.getString(7),
            preferredFoot = c.getString(8),
            heightCm = c.getInt(9),
            weightKg = c.getInt(10),
            clubId = c.getString(11),
            squadNumber = c.getInt(12),
            wageWeekly = c.getLong(13),
            contractExpiryYear = c.getInt(14),
            releaseClause = c.getLong(15),
            marketValue = c.getLong(16),
            morale = c.getInt(17),
            fitness = c.getInt(18),
            sharpness = c.getInt(19),
            reputation = c.getInt(20),
            personality = c.getString(21),
            injuryWeeks = c.getInt(22),
            seasonGoals = c.getInt(23),
            seasonAssists = c.getInt(24),
            seasonApps = c.getInt(25),
            careerGoals = c.getInt(26),
            intlCaps = c.getInt(27),
            intlGoals = c.getInt(28),
            currentAbility = c.getInt(29),
            potentialAbility = c.getInt(30),
            isYouthAcademy = c.getInt(31) == 1,
            isTransferListed = c.getInt(32) == 1,
            isLoanListed = c.getInt(33) == 1,
            loanedFromClubId = c.getString(34),
            isFictional = c.getInt(35) == 1,
            attributes = PlayerAttributes.fromJson(attrObj)
        )
    }

    fun saveOrUpdatePlayer(p: PlayerEntity) {
        val v = ContentValues().apply {
            put("id", p.id)
            put("fullName", p.fullName)
            put("birthYear", p.birthYear)
            put("age", p.age)
            put("nationality", p.nationality)
            put("secondNationality", p.secondNationality)
            put("position", p.position)
            put("secondaryPosition", p.secondaryPosition)
            put("preferredFoot", p.preferredFoot)
            put("heightCm", p.heightCm)
            put("weightKg", p.weightKg)
            put("clubId", p.clubId)
            put("squadNumber", p.squadNumber)
            put("wageWeekly", p.wageWeekly)
            put("contractExpiryYear", p.contractExpiryYear)
            put("releaseClause", p.releaseClause)
            put("marketValue", p.marketValue)
            put("morale", p.morale)
            put("fitness", p.fitness)
            put("sharpness", p.sharpness)
            put("reputation", p.reputation)
            put("personality", p.personality)
            put("injuryWeeks", p.injuryWeeks)
            put("seasonGoals", p.seasonGoals)
            put("seasonAssists", p.seasonAssists)
            put("seasonApps", p.seasonApps)
            put("careerGoals", p.careerGoals)
            put("intlCaps", p.intlCaps)
            put("intlGoals", p.intlGoals)
            put("currentAbility", p.currentAbility)
            put("potentialAbility", p.potentialAbility)
            put("isYouthAcademy", if (p.isYouthAcademy) 1 else 0)
            put("isTransferListed", if (p.isTransferListed) 1 else 0)
            put("isLoanListed", if (p.isLoanListed) 1 else 0)
            put("loanedFromClubId", p.loanedFromClubId)
            put("isFictional", if (p.isFictional) 1 else 0)
            put("attributesJson", p.attributes.toJson().toString())
        }
        writableDatabase.insertWithOnConflict("players", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePlayer(playerId: String) {
        writableDatabase.delete("players", "id = ?", arrayOf(playerId))
    }

    fun saveOrUpdateClub(club: ClubEntity) {
        val v = ContentValues().apply {
            put("id", club.id)
            put("name", club.name)
            put("shortName", club.shortName)
            put("countryId", club.countryId)
            put("divisionId", club.divisionId)
            put("city", club.city)
            put("primaryColorHex", club.primaryColorHex)
            put("secondaryColorHex", club.secondaryColorHex)
            put("reputation", club.reputation)
            put("balance", club.balance)
            put("transferBudget", club.transferBudget)
            put("wageBudgetWeekly", club.wageBudgetWeekly)
            put("debt", club.debt)
            put("stadiumName", club.stadiumName)
            put("stadiumCapacity", club.stadiumCapacity)
            put("ticketPrice", club.ticketPrice)
            put("trainingFacilityLevel", club.trainingFacilityLevel)
            put("youthFacilityLevel", club.youthFacilityLevel)
            put("medicalFacilityLevel", club.medicalFacilityLevel)
            put("scoutingNetworkLevel", club.scoutingNetworkLevel)
            put("philosophy", club.philosophy)
            put("rivalClubId", club.rivalClubId)
            put("isFictional", if (club.isFictional) 1 else 0)
            put("managerName", club.managerName)
            put("formation", club.formation)
        }
        writableDatabase.insertWithOnConflict("clubs", null, v, SQLiteDatabase.CONFLICT_REPLACE)

        // Ensure club exists in division standings
        val sCursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM standings WHERE clubId = ? AND divisionId = ?",
            arrayOf(club.id, club.divisionId)
        )
        var exists = false
        if (sCursor.moveToFirst()) exists = sCursor.getInt(0) > 0
        sCursor.close()
        if (!exists) {
            val sv = ContentValues().apply {
                put("clubId", club.id); put("clubName", club.name); put("divisionId", club.divisionId)
                put("played", 0); put("won", 0); put("drawn", 0); put("lost", 0)
                put("goalsFor", 0); put("goalsAgainst", 0); put("points", 0)
            }
            writableDatabase.insertWithOnConflict("standings", null, sv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun saveOrUpdateDivision(div: DivisionEntity) {
        val v = ContentValues().apply {
            put("id", div.id)
            put("countryId", div.countryId)
            put("name", div.name)
            put("tier", div.tier)
            put("groupIndex", div.groupIndex)
            put("teamCount", div.teamCount)
            put("promotionSpots", div.promotionSpots)
            put("playoffSpots", div.playoffSpots)
            put("relegationSpots", div.relegationSpots)
            put("pointsForWin", div.pointsForWin)
            put("prizePool", div.prizePool)
            put("isOfficialStructure", if (div.isOfficialStructure) 1 else 0)
            put("isActiveSim", if (div.isActiveSim) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("divisions", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getStandings(divisionId: String): List<StandingRow> {
        val list = mutableListOf<StandingRow>()
        val c = readableDatabase.rawQuery(
            "SELECT * FROM standings WHERE divisionId = ? ORDER BY points DESC, (goalsFor - goalsAgainst) DESC, goalsFor DESC",
            arrayOf(divisionId)
        )
        while (c.moveToNext()) {
            list.add(
                StandingRow(
                    clubId = c.getString(0),
                    clubName = c.getString(1),
                    divisionId = c.getString(2),
                    played = c.getInt(3),
                    won = c.getInt(4),
                    drawn = c.getInt(5),
                    lost = c.getInt(6),
                    goalsFor = c.getInt(7),
                    goalsAgainst = c.getInt(8),
                    points = c.getInt(9)
                )
            )
        }
        c.close()
        return list
    }

    fun getFixturesForDivision(divisionId: String, weekNumber: Int = -1): List<FixtureEntity> {
        val db = writableDatabase
        val list = mutableListOf<FixtureEntity>()
        val cursor = if (weekNumber > 0) {
            db.rawQuery("SELECT * FROM fixtures WHERE competitionId = ? AND weekNumber = ?", arrayOf(divisionId, weekNumber.toString()))
        } else {
            db.rawQuery("SELECT * FROM fixtures WHERE competitionId = ? ORDER BY weekNumber ASC", arrayOf(divisionId))
        }
        while (cursor.moveToNext()) {
            list.add(cursorToFixture(cursor))
        }
        cursor.close()

        if (list.isEmpty() && weekNumber == -1) {
            val div = getAllDivisions().find { it.id == divisionId }
            if (div != null) {
                generateFixturesForDivision(db, divisionId, div.name)
                return getFixturesForDivision(divisionId, -1)
            }
        }
        return list
    }

    private fun cursorToFixture(c: android.database.Cursor) = FixtureEntity(
        id = c.getString(0),
        competitionId = c.getString(1),
        competitionName = c.getString(2),
        isCup = c.getInt(3) == 1,
        roundNumber = c.getInt(4),
        weekNumber = c.getInt(5),
        homeClubId = c.getString(6),
        homeClubName = c.getString(7),
        awayClubId = c.getString(8),
        awayClubName = c.getString(9),
        played = c.getInt(10) == 1,
        homeGoals = c.getInt(11),
        awayGoals = c.getInt(12),
        penaltyWinnerId = c.getString(13),
        summaryText = c.getString(14)
    )

    fun getInboxMessages(): List<InboxMessage> {
        val list = mutableListOf<InboxMessage>()
        val c = readableDatabase.rawQuery("SELECT * FROM inbox ORDER BY seasonYear DESC, week DESC, rowid DESC LIMIT 60", null)
        while (c.moveToNext()) {
            list.add(
                InboxMessage(
                    id = c.getString(0),
                    week = c.getInt(1),
                    seasonYear = c.getInt(2),
                    category = c.getString(3),
                    title = c.getString(4),
                    body = c.getString(5),
                    isRead = c.getInt(6) == 1
                )
            )
        }
        c.close()
        return list
    }

    fun getHistoryRecords(): List<HistoricalRecord> {
        val list = mutableListOf<HistoricalRecord>()
        val c = readableDatabase.rawQuery("SELECT * FROM history_records ORDER BY seasonYear DESC", null)
        while (c.moveToNext()) {
            list.add(
                HistoricalRecord(
                    seasonYear = c.getInt(1),
                    competitionId = c.getString(2),
                    competitionName = c.getString(3),
                    winnerClubName = c.getString(4),
                    runnerUpClubName = c.getString(5),
                    topScorerName = c.getString(6),
                    topScorerGoals = c.getInt(7)
                )
            )
        }
        c.close()
        return list
    }

    // ---------------------------------------------------------------------
    // JSON & CSV DATABASE EXPORT / IMPORT FOR EDITOR & CUSTOM DATABASES
    // ---------------------------------------------------------------------
    fun exportCustomDatabaseJson(divisionId: String): String {
        val root = JSONObject()
        val clubsArr = JSONArray()
        getClubsByDivision(divisionId).forEach { club ->
            clubsArr.put(JSONObject().apply {
                put("id", club.id); put("name", club.name); put("countryId", club.countryId)
                put("divisionId", club.divisionId); put("reputation", club.reputation)
                put("balance", club.balance); put("transferBudget", club.transferBudget)
                put("stadiumName", club.stadiumName); put("stadiumCapacity", club.stadiumCapacity)
            })
        }
        root.put("divisionId", divisionId)
        root.put("clubs", clubsArr)
        return root.toString(2)
    }

    fun importCustomDatabaseJson(jsonString: String): Int {
        val root = JSONObject(jsonString)
        val clubsArr = root.optJSONArray("clubs") ?: return 0
        var imported = 0
        for (i in 0 until clubsArr.length()) {
            val o = clubsArr.getJSONObject(i)
            val existing = getClubById(o.optString("id"))
            val updated = (existing ?: ClubEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                shortName = o.getString("name").take(12),
                countryId = o.optString("countryId", "ENG"),
                divisionId = o.optString("divisionId", "ENG_D1"),
                city = "Custom City",
                primaryColorHex = 0xFF1976D2L,
                secondaryColorHex = 0xFFFFFFFFL,
                reputation = o.optInt("reputation", 70),
                balance = o.optLong("balance", 10_000_000L),
                transferBudget = o.optLong("transferBudget", 5_000_000L),
                wageBudgetWeekly = 150_000L,
                debt = 0L,
                stadiumName = o.optString("stadiumName", "Custom Stadium"),
                stadiumCapacity = o.optInt("stadiumCapacity", 25000),
                ticketPrice = 30,
                trainingFacilityLevel = 12,
                youthFacilityLevel = 12,
                medicalFacilityLevel = 12,
                scoutingNetworkLevel = 12,
                philosophy = "Balanced",
                rivalClubId = "",
                isFictional = false,
                managerName = "Imported Manager"
            )).copy(
                name = o.optString("name", existing?.name ?: "Club"),
                reputation = o.optInt("reputation", existing?.reputation ?: 70),
                balance = o.optLong("balance", existing?.balance ?: 10_000_000L),
                transferBudget = o.optLong("transferBudget", existing?.transferBudget ?: 5_000_000L)
            )
            saveOrUpdateClub(updated)
            imported++
        }
        return imported
    }

    fun resetEntireDatabase() {
        val db = writableDatabase
        db.execSQL("DELETE FROM divisions")
        db.execSQL("DELETE FROM clubs")
        db.execSQL("DELETE FROM players")
        db.execSQL("DELETE FROM standings")
        db.execSQL("DELETE FROM fixtures")
        db.execSQL("DELETE FROM inbox")
        db.execSQL("DELETE FROM history_records")
        ensureWorldSeeded()
    }
}
