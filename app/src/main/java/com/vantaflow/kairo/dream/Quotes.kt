package com.vantaflow.kairo.dream

import java.time.LocalDate

/** Une citation et son auteur. */
data class Quote(val text: String, val author: String)

/** Citations de l'écran de veille (dans l'appli : aucune connexion nécessaire). */
val Quotes = listOf(
    Quote("On ne voit bien qu'avec le cœur. L'essentiel est invisible pour les yeux.", "Antoine de Saint-Exupéry"),
    Quote("Il faut toujours viser la lune, car même en cas d'échec, on atterrit dans les étoiles.", "Les Brown"),
    Quote("La simplicité est la sophistication suprême.", "Léonard de Vinci"),
    Quote("Le seul vrai voyage, ce serait d'avoir d'autres yeux.", "Marcel Proust"),
    Quote("Choisissez un travail que vous aimez et vous n'aurez pas à travailler un seul jour de votre vie.", "Proverbe"),
    Quote("La patience est amère, mais son fruit est doux.", "Jean-Jacques Rousseau"),
    Quote("Je sais que je ne sais rien.", "Socrate"),
    Quote("Il n'y a qu'une façon d'échouer, c'est d'abandonner avant d'avoir réussi.", "Proverbe"),
    Quote("Rien n'est permanent, sauf le changement.", "Héraclite"),
    Quote("Celui qui déplace une montagne commence par déplacer de petites pierres.", "Proverbe chinois"),
    Quote("Sans la musique, la vie serait une erreur.", "Friedrich Nietzsche"),
    Quote("Un sourire coûte moins cher que l'électricité, mais donne autant de lumière.", "Proverbe"),
    Quote("Le génie est fait d'un pour cent d'inspiration et de quatre-vingt-dix-neuf pour cent de transpiration.", "Thomas Edison"),
    Quote("Fais de ta vie un rêve, et d'un rêve, une réalité.", "Antoine de Saint-Exupéry"),
    Quote("Le temps est un grand maître, dit-on. Le malheur est qu'il tue ses élèves.", "Hector Berlioz"),
    Quote("Ce que nous savons est une goutte d'eau ; ce que nous ignorons est un océan.", "Attribué à Isaac Newton"),
    Quote("La gratitude est la mémoire du cœur.", "Jean-Baptiste Massieu"),
    Quote("L'imagination est plus importante que le savoir.", "Albert Einstein"),
    Quote("Vivre, c'est naître sans cesse.", "Antoine de Saint-Exupéry"),
    Quote("La lecture est une amitié.", "Marcel Proust"),
    Quote("Le monde est un livre, et ceux qui ne voyagent pas n'en lisent qu'une page.", "Saint Augustin"),
    Quote("Ne juge pas chaque jour à la récolte que tu fais, mais aux graines que tu sèmes.", "Robert Louis Stevenson"),
    Quote("Le courage n'est pas l'absence de peur, mais la capacité de la vaincre.", "Nelson Mandela"),
    Quote("Tout semble impossible jusqu'à ce que ce soit fait.", "Nelson Mandela"),
    Quote("Chaque jour est une petite vie.", "Arthur Schopenhauer"),
    Quote("Le meilleur moyen de prédire l'avenir, c'est de l'inventer.", "Alan Kay"),
    Quote("Qui veut voyager loin ménage sa monture.", "Jean Racine"),
    Quote("Nous sommes ce que nous faisons de manière répétée.", "Will Durant, d'après Aristote"),
    Quote("Celui qui a un pourquoi qui lui tient lieu de but peut vivre avec n'importe quel comment.", "Friedrich Nietzsche"),
    Quote("Il faut cultiver notre jardin.", "Voltaire"),
    Quote("Le cœur a ses raisons que la raison ne connaît point.", "Blaise Pascal"),
    Quote("Ce qui ne me tue pas me rend plus fort.", "Friedrich Nietzsche"),
    Quote("La beauté est dans les yeux de celui qui regarde.", "Oscar Wilde"),
    Quote("Soyez vous-même, les autres sont déjà pris.", "Oscar Wilde"),
    Quote("L'avenir appartient à ceux qui se lèvent tôt.", "Proverbe"),
    Quote("Petit à petit, l'oiseau fait son nid.", "Proverbe"),
    Quote("Cherchez le savoir, fût-ce jusqu'en Chine.", "Proverbe arabe"),
    Quote("La patience est la clé du soulagement.", "Proverbe arabe"),
    Quote("Celui qui sait ne parle pas, celui qui parle ne sait pas.", "Lao Tseu"),
    Quote("Un voyage de mille lieues commence par un premier pas.", "Lao Tseu"),
    Quote("Le bonheur n'est pas une destination, c'est une façon de voyager.", "Margaret Lee Runbeck"),
    Quote("La vraie générosité envers l'avenir consiste à tout donner au présent.", "Albert Camus"),
    Quote("Au milieu de l'hiver, j'ai découvert en moi un invincible été.", "Albert Camus"),
    Quote("Il n'y a pas de chemin vers le bonheur : le bonheur, c'est le chemin.", "Proverbe"),
    Quote("Les grandes choses sont faites par une série de petites choses réunies.", "Vincent Van Gogh"),
    Quote("Je rêve de peindre, et je peins mon rêve.", "Vincent Van Gogh"),
    Quote("La créativité, c'est l'intelligence qui s'amuse.", "Proverbe"),
)

/** Citation du jour : la même toute la journée, une autre le lendemain. */
fun quoteOfDay(date: LocalDate): Quote = Quotes[Math.floorMod(date.toEpochDay(), Quotes.size.toLong()).toInt()]
