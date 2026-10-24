package uk.owen.starsigns

/** A star with J2000 coordinates. [ra] in hours, [dec] in degrees. */
data class Star(val name: String, val ra: Double, val dec: Double, val mag: Double)

data class Constellation(
    val name: String,
    val symbol: String,          // e.g. ♈ (empty for non-zodiac)
    val meaning: String,
    val signDates: String,       // tropical sun-sign dates (empty for non-zodiac)
    val stars: List<Star>,
    val lines: List<Pair<Int, Int>>,
    val zodiac: Boolean = true,
)

private fun s(name: String, ra: Double, dec: Double, mag: Double) = Star(name, ra, dec, mag)

/** Builds line list from chains of star indices, e.g. chain(0,1,2) -> (0,1),(1,2). */
private fun chains(vararg c: IntArray): List<Pair<Int, Int>> =
    c.flatMap { ch -> (0 until ch.size - 1).map { ch[it] to ch[it + 1] } }

private fun ch(vararg i: Int) = i

object Catalog {

    val constellations: List<Constellation> = listOf(
        Constellation(
            "Aries", "♈", "The Ram", "21 Mar – 19 Apr",
            listOf(
                s("Mesarthim", 1.8922, 19.294, 3.9),
                s("Sheratan", 1.9107, 20.808, 2.6),
                s("Hamal", 2.1196, 23.4625, 2.0),
                s("41 Ari", 2.8331, 27.261, 3.6),
            ),
            chains(ch(0, 1, 2, 3)),
        ),
        Constellation(
            "Taurus", "♉", "The Bull", "20 Apr – 20 May",
            listOf(
                s("Aldebaran", 4.5987, 16.509, 0.85),
                s("Elnath", 5.4382, 28.607, 1.65),
                s("Tianguan (ζ Tau)", 5.6274, 21.143, 3.0),
                s("Prima Hyadum (γ Tau)", 4.3299, 15.628, 3.65),
                s("δ¹ Tau", 4.3823, 17.543, 3.76),
                s("Ain (ε Tau)", 4.4770, 19.180, 3.53),
                s("θ² Tau", 4.4777, 15.871, 3.4),
                s("λ Tau", 4.0113, 12.490, 3.47),
                s("ο Tau", 3.4136, 9.029, 3.6),
                s("Pleiades", 3.7914, 24.105, 1.6),
            ),
            chains(ch(2, 0, 6, 3, 4, 5, 1), ch(3, 7, 8)),
        ),
        Constellation(
            "Gemini", "♊", "The Twins", "21 May – 20 Jun",
            listOf(
                s("Castor", 7.5767, 31.888, 1.58),
                s("Pollux", 7.7554, 28.026, 1.14),
                s("Alhena", 6.6285, 16.399, 1.93),
                s("Mebsuta", 6.7322, 25.131, 3.0),
                s("Tejat", 6.3827, 22.514, 2.9),
                s("Propus", 6.2479, 22.507, 3.3),
                s("Wasat", 7.3354, 21.982, 3.5),
                s("Mekbuda", 7.0685, 20.570, 3.9),
                s("τ Gem", 7.1857, 30.245, 4.4),
                s("θ Gem", 6.8797, 33.961, 3.6),
                s("ξ Gem", 6.7547, 12.896, 3.36),
                s("λ Gem", 7.3016, 16.540, 3.58),
                s("ι Gem", 7.4288, 27.798, 3.79),
                s("υ Gem", 7.5986, 26.896, 4.06),
            ),
            chains(
                ch(0, 8, 3, 4, 5), ch(8, 9),
                ch(1, 13, 12, 8), ch(13, 6, 7, 2), ch(6, 11, 10),
            ),
        ),
        Constellation(
            "Cancer", "♋", "The Crab", "21 Jun – 22 Jul",
            listOf(
                s("Acubens", 8.9748, 11.858, 4.25),
                s("Al Tarf", 8.2753, 9.186, 3.5),
                s("Asellus Australis", 8.7447, 18.154, 3.94),
                s("Asellus Borealis", 8.7214, 21.469, 4.66),
                s("ι Cnc", 8.7783, 28.760, 4.0),
            ),
            chains(ch(1, 2, 0), ch(2, 3, 4)),
        ),
        Constellation(
            "Leo", "♌", "The Lion", "23 Jul – 22 Aug",
            listOf(
                s("Regulus", 10.1395, 11.967, 1.35),
                s("Denebola", 11.8177, 14.572, 2.1),
                s("Algieba", 10.3329, 19.842, 2.0),
                s("Zosma", 11.2351, 20.524, 2.56),
                s("Chertan", 11.2373, 15.430, 3.3),
                s("η Leo", 10.1222, 16.763, 3.5),
                s("Adhafera", 10.2782, 23.417, 3.4),
                s("Rasalas", 9.8794, 26.007, 3.9),
                s("Algenubi", 9.7642, 23.774, 2.98),
            ),
            chains(ch(0, 5, 2, 6, 7, 8), ch(2, 3, 1, 4, 0)),
        ),
        Constellation(
            "Virgo", "♍", "The Maiden", "23 Aug – 22 Sep",
            listOf(
                s("Spica", 13.4199, -11.161, 0.97),
                s("Porrima", 12.6943, -1.449, 2.74),
                s("Vindemiatrix", 13.0363, 10.959, 2.8),
                s("Minelauva (δ Vir)", 12.9267, 3.398, 3.38),
                s("Heze (ζ Vir)", 13.5782, -0.596, 3.37),
                s("Zavijava", 11.8449, 1.765, 3.6),
                s("Zaniah", 12.3318, -0.667, 3.9),
                s("109 Vir", 14.7708, 1.893, 3.7),
                s("θ Vir", 13.1658, -5.539, 4.4),
            ),
            chains(ch(5, 6, 1, 3, 2), ch(1, 8, 0), ch(3, 4, 7)),
        ),
        Constellation(
            "Libra", "♎", "The Scales", "23 Sep – 22 Oct",
            listOf(
                s("Zubenelgenubi", 14.8480, -16.042, 2.75),
                s("Zubeneschamali", 15.2834, -9.383, 2.6),
                s("Brachium (σ Lib)", 15.0678, -25.282, 3.3),
                s("Zubenelhakrabi (γ Lib)", 15.5921, -14.789, 3.9),
                s("υ Lib", 15.6171, -28.135, 3.6),
                s("τ Lib", 15.6443, -29.778, 3.66),
            ),
            chains(ch(2, 0, 1, 3, 0), ch(3, 4, 5)),
        ),
        Constellation(
            "Scorpius", "♏", "The Scorpion", "23 Oct – 21 Nov",
            listOf(
                s("Antares", 16.4901, -26.432, 1.06),
                s("Acrab", 16.0906, -19.805, 2.6),
                s("Dschubba", 16.0056, -22.622, 2.3),
                s("Fang (π Sco)", 15.9809, -26.114, 2.9),
                s("Alniyat (σ Sco)", 16.3531, -25.593, 2.9),
                s("Paikauhale (τ Sco)", 16.5980, -28.216, 2.8),
                s("Larawag (ε Sco)", 16.8360, -34.293, 2.3),
                s("Xamidimura (μ¹ Sco)", 16.8645, -38.048, 3.0),
                s("ζ² Sco", 16.9097, -42.361, 3.6),
                s("η Sco", 17.2026, -43.239, 3.3),
                s("Sargas", 17.6220, -42.998, 1.86),
                s("ι¹ Sco", 17.7931, -40.127, 3.0),
                s("Girtab (κ Sco)", 17.7081, -39.030, 2.4),
                s("Shaula", 17.5601, -37.104, 1.62),
                s("Lesath", 17.5127, -37.296, 2.7),
            ),
            chains(ch(1, 2, 3), ch(2, 4, 0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)),
        ),
        Constellation(
            "Sagittarius", "♐", "The Archer", "22 Nov – 21 Dec",
            listOf(
                s("Kaus Australis", 18.4029, -34.385, 1.85),
                s("Nunki", 18.9211, -26.297, 2.05),
                s("Ascella", 19.0435, -29.880, 2.6),
                s("Kaus Media", 18.3499, -29.828, 2.7),
                s("Kaus Borealis", 18.4662, -25.422, 2.8),
                s("Alnasl", 18.0968, -30.424, 3.0),
                s("φ Sgr", 18.7609, -26.991, 3.2),
                s("τ Sgr", 19.1157, -27.670, 3.3),
                s("η Sgr", 18.2938, -36.762, 3.1),
            ),
            chains(ch(5, 3, 0, 5), ch(3, 4, 6, 3), ch(6, 1, 7, 2, 6), ch(2, 0, 8)),
        ),
        Constellation(
            "Capricornus", "♑", "The Sea-Goat", "22 Dec – 19 Jan",
            listOf(
                s("Algedi", 20.3009, -12.545, 3.6),
                s("Dabih", 20.3502, -14.781, 3.05),
                s("Deneb Algedi", 21.7840, -16.127, 2.85),
                s("Nashira", 21.6682, -16.662, 3.7),
                s("ζ Cap", 21.4444, -22.411, 3.7),
                s("ω Cap", 20.8637, -26.919, 4.1),
                s("ψ Cap", 20.7683, -25.271, 4.1),
                s("θ Cap", 21.0991, -17.233, 4.1),
            ),
            chains(ch(0, 1, 6, 5, 4, 2, 3, 7, 0)),
        ),
        Constellation(
            "Aquarius", "♒", "The Water-Bearer", "20 Jan – 18 Feb",
            listOf(
                s("Albali", 20.7946, -9.496, 3.77),
                s("Sadalsuud", 21.5260, -5.571, 2.9),
                s("Sadalmelik", 22.0964, -0.320, 2.95),
                s("Sadachbia", 22.3609, -1.387, 3.85),
                s("ζ Aqr", 22.4806, -0.020, 3.65),
                s("η Aqr", 22.5893, -0.117, 4.0),
                s("Seat (π Aqr)", 22.4213, 1.378, 4.6),
                s("Ancha (θ Aqr)", 22.2806, -7.783, 4.16),
                s("λ Aqr", 22.8769, -7.580, 3.7),
                s("τ² Aqr", 22.8265, -13.593, 4.0),
                s("Skat", 22.9108, -15.821, 3.27),
                s("88 Aqr", 23.1574, -21.173, 3.66),
            ),
            chains(ch(0, 1, 2, 3, 4, 5), ch(4, 6), ch(2, 7, 8, 9, 10, 11)),
        ),
        Constellation(
            "Pisces", "♓", "The Fishes", "19 Feb – 20 Mar",
            listOf(
                s("γ Psc", 23.2861, 3.282, 3.7),
                s("κ Psc", 23.4489, 1.256, 4.9),
                s("λ Psc", 23.7008, 1.780, 4.5),
                s("ι Psc", 23.6658, 5.626, 4.1),
                s("θ Psc", 23.4661, 6.379, 4.3),
                s("ω Psc", 23.9885, 6.863, 4.0),
                s("δ Psc", 0.8114, 7.585, 4.4),
                s("ε Psc", 1.0491, 7.890, 4.3),
                s("Revati (ζ Psc)", 1.2289, 7.576, 5.2),
                s("μ Psc", 1.5031, 6.144, 4.8),
                s("ν Psc", 1.6905, 5.488, 4.4),
                s("Alrescha", 2.0341, 2.764, 3.8),
                s("Torcular (ο Psc)", 1.7566, 9.158, 4.3),
                s("Alpherg", 1.5247, 15.346, 3.6),
                s("φ Psc", 1.2292, 24.584, 4.65),
                s("υ Psc", 1.3245, 27.264, 4.7),
                s("τ Psc", 1.1943, 30.090, 4.5),
            ),
            chains(
                ch(0, 1, 2, 3, 4, 0), ch(3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            ),
        ),

        // Landmark (non-zodiac) constellations to help you get your bearings.
        Constellation(
            "Orion", "", "The Hunter", "",
            listOf(
                s("Betelgeuse", 5.9195, 7.407, 0.5),
                s("Rigel", 5.2423, -8.202, 0.13),
                s("Bellatrix", 5.4189, 6.350, 1.64),
                s("Saiph", 5.7959, -9.670, 2.06),
                s("Alnitak", 5.6793, -1.943, 1.77),
                s("Alnilam", 5.6036, -1.202, 1.69),
                s("Mintaka", 5.5334, -0.299, 2.25),
                s("Meissa", 5.5856, 9.934, 3.4),
            ),
            chains(ch(0, 7, 2), ch(0, 4, 5, 6, 2), ch(4, 3), ch(6, 1)),
            zodiac = false,
        ),
        Constellation(
            "The Plough", "", "Part of Ursa Major", "",
            listOf(
                s("Dubhe", 11.0621, 61.751, 1.8),
                s("Merak", 11.0307, 56.383, 2.37),
                s("Phecda", 11.8972, 53.695, 2.44),
                s("Megrez", 12.2571, 57.033, 3.3),
                s("Alioth", 12.9005, 55.960, 1.77),
                s("Mizar", 13.3988, 54.925, 2.23),
                s("Alkaid", 13.7923, 49.313, 1.86),
            ),
            chains(ch(0, 1, 2, 3, 0), ch(3, 4, 5, 6)),
            zodiac = false,
        ),
        Constellation(
            "Cassiopeia", "", "The Queen", "",
            listOf(
                s("Caph", 0.1530, 59.150, 2.28),
                s("Schedar", 0.6751, 56.537, 2.24),
                s("γ Cas", 0.9451, 60.717, 2.15),
                s("Ruchbah", 1.4303, 60.235, 2.66),
                s("Segin", 1.9066, 63.670, 3.35),
            ),
            chains(ch(0, 1, 2, 3, 4)),
            zodiac = false,
        ),
        Constellation(
            "Polaris", "", "The North Star", "",
            listOf(s("Polaris", 2.5303, 89.264, 1.98)),
            emptyList(),
            zodiac = false,
        ),
    )
}
