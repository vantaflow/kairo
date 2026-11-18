package com.vantaflow.kairo.dream

/**
 * Citations de l'écran de veille en anglais : même taille et même ordre que [Quotes] (indice par indice),
 * pour que la citation du jour soit la même dans toutes les langues.
 */
val QuotesEn: List<Quote> = listOf(
    Quote("Slow and steady wins the race.", "Jean de La Fontaine"), // 0
    Quote("We often need someone smaller than ourselves.", "Jean de La Fontaine"), // 1
    Quote("Patience and time do more than strength or rage.", "Jean de La Fontaine"), // 2
    Quote("Every flatterer lives at the expense of those who listen to him.", "Jean de La Fontaine"), // 3
    Quote("Heaven helps those who help themselves.", "Jean de La Fontaine"), // 4
    Quote("Work hard and take pains: it is the capital that never runs short.", "Jean de La Fontaine"), // 5
    Quote("We must help one another: it is the law of nature.", "Jean de La Fontaine"), // 6
    Quote("What a sweet thing a true friend is!", "Jean de La Fontaine"), // 7
    Quote("You cannot please everyone.", "Jean de La Fontaine"), // 8
    Quote("Little fish will grow big, if God grants it life.", "Jean de La Fontaine"), // 9
    Quote("Distrust is the mother of safety.", "Jean de La Fontaine"), // 10
    Quote("A bird in the hand is worth two in the bush.", "Jean de La Fontaine"), // 11
    Quote("In everything, one must consider the end.", "Jean de La Fontaine"), // 12
    Quote("We all have strength enough to bear the misfortunes of others.", "La Rochefoucauld"), // 13
    Quote("We are never so happy or so unhappy as we imagine.", "La Rochefoucauld"), // 14
    Quote("It is easier to be wise for others than for ourselves.", "La Rochefoucauld"), // 15
    Quote("It is great folly to wish to be wise all alone.", "La Rochefoucauld"), // 16
    Quote("Absence diminishes small passions and increases great ones, as the wind blows out candles and fans a fire.", "La Rochefoucauld"), // 17
    Quote("It is more shameful to distrust our friends than to be deceived by them.", "La Rochefoucauld"), // 18
    Quote("Time, which strengthens friendship, weakens love.", "Jean de La Bruyère"), // 19
    Quote("Great thoughts come from the heart.", "Vauvenargues"), // 20
    Quote("The most wasted of all days is one without laughter.", "Chamfort"), // 21
    Quote("To teach is to learn twice.", "Joseph Joubert"), // 22
    Quote("To live happily, live hidden.", "Jean-Pierre Claris de Florian"), // 23
    Quote("To each his own trade, and the cows will be well kept.", "Jean-Pierre Claris de Florian"), // 24
    Quote("Drive out nature with a pitchfork, and it comes galloping back.", "Destouches"), // 25
    Quote("Criticism is easy, art is difficult.", "Destouches"), // 26
    Quote("Punctuality is the politeness of kings.", "Louis XVIII"), // 27
    Quote("Man is but a reed, the weakest in nature, but he is a thinking reed.", "Blaise Pascal"), // 28
    Quote("The heart has its reasons which reason knows nothing of.", "Blaise Pascal"), // 29
    Quote("All of humanity's problems stem from man's inability to sit quietly in a room alone.", "Blaise Pascal"), // 30
    Quote("I have made this longer than usual because I have not had time to make it shorter.", "Blaise Pascal"), // 31
    Quote("Because it was him, because it was me.", "Michel de Montaigne"), // 32
    Quote("I want death to find me planting my cabbages.", "Michel de Montaigne"), // 33
    Quote("Every man bears the whole form of the human condition.", "Michel de Montaigne"), // 34
    Quote("Better a well-made head than a well-filled one.", "Michel de Montaigne"), // 35
    Quote("What do I know?", "Michel de Montaigne"), // 36
    Quote("I think, therefore I am.", "René Descartes"), // 37
    Quote("Good sense is the most fairly distributed thing in the world.", "René Descartes"), // 38
    Quote("We must cultivate our garden.", "Voltaire"), // 39
    Quote("The perfect is the enemy of the good.", "Voltaire"), // 40
    Quote("Work keeps at bay three great evils: boredom, vice and need.", "Voltaire"), // 41
    Quote("The secret of being a bore is to tell everything.", "Voltaire"), // 42
    Quote("It is better to risk saving a guilty person than to condemn an innocent one.", "Voltaire"), // 43
    Quote("Man is born free, and everywhere he is in chains.", "Jean-Jacques Rousseau"), // 44
    Quote("One must have studied much to know little.", "Montesquieu"), // 45
    Quote("Science without conscience is but the ruin of the soul.", "François Rabelais"), // 46
    Quote("Laughter is what makes us human.", "François Rabelais"), // 47
    Quote("One should eat to live, not live to eat.", "Molière"), // 48
    Quote("Time has nothing to do with it.", "Molière"), // 49
    Quote("It is a long road from the plan to the thing.", "Molière"), // 50
    Quote("To conquer without risk is to triumph without glory.", "Pierre Corneille"), // 51
    Quote("In noble souls, valour does not wait for the number of years.", "Pierre Corneille"), // 52
    Quote("He who wants to travel far spares his mount.", "Jean Racine"), // 53
    Quote("What is well conceived is clearly said, and the words to say it flow with ease.", "Nicolas Boileau"), // 54
    Quote("Make haste slowly, and without losing heart, put your work back on the loom twenty times.", "Nicolas Boileau"), // 55
    Quote("He who cannot limit himself never knew how to write.", "Nicolas Boileau"), // 56
    Quote("I hasten to laugh at everything, for fear of having to weep.", "Beaumarchais"), // 57
    Quote("Without the freedom to criticize, there is no flattering praise.", "Beaumarchais"), // 58
    Quote("Forests precede civilizations, deserts follow them.", "Chateaubriand"), // 59
    Quote("One person is missing, and the whole world seems empty.", "Alphonse de Lamartine"), // 60
    Quote("O time, suspend your flight!", "Alphonse de Lamartine"), // 61
    Quote("Those who live are those who fight.", "Victor Hugo"), // 62
    Quote("Music expresses that which cannot be said and on which it is impossible to be silent.", "Victor Hugo"), // 63
    Quote("Laughter is the sun that drives winter from the human face.", "Victor Hugo"), // 64
    Quote("Form is substance rising to the surface.", "Victor Hugo"), // 65
    Quote("To love or to have loved, that is enough.", "Victor Hugo"), // 66
    Quote("One resists the invasion of armies; one does not resist the invasion of ideas.", "Victor Hugo"), // 67
    Quote("All human wisdom is contained in these two words: wait and hope.", "Alexandre Dumas"), // 68
    Quote("All for one, one for all.", "Alexandre Dumas"), // 69
    Quote("There is only one happiness in life: to love and be loved.", "George Sand"), // 70
    Quote("Beauty is nothing but the promise of happiness.", "Stendhal"), // 71
    Quote("Glory is the sun of the dead.", "Honoré de Balzac"), // 72
    Quote("Perfumes, colours and sounds answer one another.", "Charles Baudelaire"), // 73
    Quote("I is another.", "Arthur Rimbaud"), // 74
    Quote("One must be absolutely modern.", "Arthur Rimbaud"), // 75
    Quote("What does the bottle matter, so long as one gets drunk?", "Alfred de Musset"), // 76
    Quote("No trifling with love.", "Alfred de Musset"), // 77
    Quote("To leave is to die a little.", "Edmond Haraucourt"), // 78
    Quote("It is high time to relight the stars.", "Guillaume Apollinaire"), // 79
    Quote("The wind is rising!… We must try to live!", "Paul Valéry"), // 80
    Quote("Laziness is the habit of resting before you get tired.", "Jules Renard"), // 81
    Quote("To choose is to renounce.", "André Gide"), // 82
    Quote("Follow your bent, provided it leads upward.", "André Gide"), // 83
    Quote("The only real voyage of discovery consists not in seeking new landscapes, but in having new eyes.", "Marcel Proust"), // 84
    Quote("We should try to be happy, if only to set an example.", "Jacques Prévert"), // 85
    Quote("One must imagine Sisyphus happy.", "Albert Camus"), // 86
    Quote("In the midst of winter, I found there was, within me, an invincible summer.", "Albert Camus"), // 87
    Quote("Real generosity towards the future lies in giving all to the present.", "Albert Camus"), // 88
    Quote("To misname things is to add to the misfortune of the world.", "Albert Camus"), // 89
    Quote("The struggle itself toward the heights is enough to fill a man's heart.", "Albert Camus"), // 90
    Quote("Hell is other people.", "Jean-Paul Sartre"), // 91
    Quote("Man is condemned to be free.", "Jean-Paul Sartre"), // 92
    Quote("One is not born, but rather becomes, a woman.", "Simone de Beauvoir"), // 93
    Quote("Courage is to seek the truth and to speak it.", "Jean Jaurès"), // 94
    Quote("The important thing in life is not the triumph but the struggle.", "Pierre de Coubertin"), // 95
    Quote("Chance favours only the prepared mind.", "Louis Pasteur"), // 96
    Quote("Nothing is lost, nothing is created, everything is transformed.", "After Lavoisier"), // 97
    Quote("Nothing in life is to be feared, it is only to be understood.", "Marie Curie"), // 98
    Quote("The greatest good we can do for others is not to share our riches with them, but to reveal theirs to them.", "Louis Lavelle"), // 99
    Quote("Impossible is not French.", "Napoleon I"), // 100
    Quote("One sees clearly only with the heart. What is essential is invisible to the eye.", "Antoine de Saint-Exupéry"), // 101
    Quote("It is the time you have wasted for your rose that makes your rose so important.", "Antoine de Saint-Exupéry"), // 102
    Quote("You become responsible, forever, for what you have tamed.", "Antoine de Saint-Exupéry"), // 103
    Quote("All grown-ups were once children, but only few of them remember it.", "Antoine de Saint-Exupéry"), // 104
    Quote("What makes the desert beautiful is that somewhere it hides a well.", "Antoine de Saint-Exupéry"), // 105
    Quote("Love does not consist in gazing at each other, but in looking outward together in the same direction.", "Antoine de Saint-Exupéry"), // 106
    Quote("As for the future, your task is not to foresee it, but to enable it.", "Antoine de Saint-Exupéry"), // 107
    Quote("Perfection is achieved not when there is nothing more to add, but when there is nothing left to take away.", "Antoine de Saint-Exupéry"), // 108
    Quote("It is much more difficult to judge oneself than to judge others.", "Antoine de Saint-Exupéry"), // 109
    Quote("Grown-ups never understand anything by themselves.", "Antoine de Saint-Exupéry"), // 110
    Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"), // 111
    Quote("If one does not know to which port one is sailing, no wind is favourable.", "Seneca"), // 112
    Quote("While we postpone, life speeds by.", "Seneca"), // 113
    Quote("Men are disturbed not by things, but by the views which they take of things.", "Epictetus"), // 114
    Quote("Perform every act in life as though it were your last.", "Marcus Aurelius"), // 115
    Quote("No man ever steps in the same river twice.", "Heraclitus"), // 116
    Quote("The only constant is change.", "Heraclitus"), // 117
    Quote("I know that I know nothing.", "Socrates"), // 118
    Quote("Know thyself.", "Temple of Delphi"), // 119
    Quote("One swallow does not make a summer.", "Aristotle"), // 120
    Quote("The roots of education are bitter, but the fruit is sweet.", "After Aristotle"), // 121
    Quote("Give me a place to stand, and I will move the world.", "Archimedes"), // 122
    Quote("I came, I saw, I conquered.", "Julius Caesar"), // 123
    Quote("Seize the day, trusting as little as possible in tomorrow.", "Horace"), // 124
    Quote("A sound mind in a sound body.", "Juvenal"), // 125
    Quote("Make haste slowly.", "Augustus"), // 126
    Quote("Fortune favours the bold.", "Virgil"), // 127
    Quote("Time flies, never to return.", "Virgil"), // 128
    Quote("To every thing there is a season, and a time to every purpose under the heaven.", "Ecclesiastes"), // 129
    Quote("There is nothing new under the sun.", "Ecclesiastes"), // 130
    Quote("He that walketh with wise men shall be wise.", "Book of Proverbs"), // 131
    Quote("A merry heart doeth good like a medicine.", "Book of Proverbs"), // 132
    Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"), // 133
    Quote("He who knows others is wise; he who knows himself is enlightened.", "Lao Tzu"), // 134
    Quote("He who knows he has enough is rich.", "Lao Tzu"), // 135
    Quote("Those who know do not speak; those who speak do not know.", "Lao Tzu"), // 136
    Quote("Learning without thought is labour lost; thought without learning is perilous.", "Confucius"), // 137
    Quote("Do not do to others what you do not want done to yourself.", "Confucius"), // 138
    Quote("Demand much from yourself and expect little from others.", "Confucius"), // 139
    Quote("When three people walk together, one of them is sure to be my teacher.", "Confucius"), // 140
    Quote("To know what you know and to know what you do not know: that is true knowledge.", "Confucius"), // 141
    Quote("The wise man seeks within himself the cause of his faults; the fool seeks it in others.", "Confucius"), // 142
    Quote("The best companion, at all times, is a book.", "Al-Mutanabbi"), // 143
    Quote("Not everything a man wishes for comes to him: the winds blow where the ships do not desire.", "Al-Mutanabbi"), // 144
    Quote("People are enemies of what they do not know.", "Ali ibn Abi Talib"), // 145
    Quote("The worth of every man lies in the good he does.", "Ali ibn Abi Talib"), // 146
    Quote("The vanquished always seek to imitate the victor.", "Ibn Khaldun"), // 147
    Quote("Your children are not your children. They are the sons and daughters of Life's longing for itself.", "Kahlil Gibran"), // 148
    Quote("Work is love made visible.", "Kahlil Gibran"), // 149
    Quote("Yesterday is but today's memory, and tomorrow is today's dream.", "Kahlil Gibran"), // 150
    Quote("On this earth, there is what makes life worth living.", "Mahmoud Darwish"), // 151
    Quote("When an old man dies, a library burns.", "Amadou Hampâté Bâ"), // 152
    Quote("Life is like riding a bicycle. To keep your balance, you must keep moving.", "Albert Einstein"), // 153
    Quote("Imagination is more important than knowledge.", "Albert Einstein"), // 154
    Quote("If I have seen further, it is by standing on the shoulders of giants.", "Isaac Newton"), // 155
    Quote("Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"), // 156
    Quote("Education is the most powerful weapon which you can use to change the world.", "Nelson Mandela"), // 157
    Quote("We must learn to live together as brothers or perish together as fools.", "Martin Luther King Jr."), // 158
    Quote("To be, or not to be, that is the question.", "William Shakespeare"), // 159
    Quote("All the world's a stage.", "William Shakespeare"), // 160
    Quote("Without music, life would be a mistake.", "Friedrich Nietzsche"), // 161
    Quote("What does not kill me makes me stronger.", "Friedrich Nietzsche"), // 162
    Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"), // 163
    Quote("Beauty is in the eye of the beholder.", "Margaret Wolfe Hungerford"), // 164
    Quote("I do not seek, I find.", "Pablo Picasso"), // 165
    Quote("Great things are not done by impulse, but by a series of small things brought together.", "Vincent van Gogh"), // 166
    Quote("I dream of painting and then I paint my dream.", "Vincent van Gogh"), // 167
    Quote("Life is what happens to you while you're busy making other plans.", "John Lennon"), // 168
    Quote("Stay hungry, stay foolish.", "Steve Jobs"), // 169
    Quote("We make the road by walking.", "Antonio Machado"), // 170
    Quote("The best way to predict the future is to invent it.", "Alan Kay"), // 171
    Quote("Great works are performed not by strength but by perseverance.", "Samuel Johnson"), // 172
    Quote("Those who cannot remember the past are condemned to repeat it.", "After George Santayana"), // 173
    Quote("Happiness is the only thing that doubles when you share it.", "Albert Schweitzer"), // 174
    Quote("Sow the wind and reap the whirlwind.", "French proverb"), // 175
    Quote("Clothes don't make the man.", "French proverb"), // 176
    Quote("After rain comes sunshine.", "French proverb"), // 177
    Quote("Don't sell the bear's skin before you've caught the bear.", "French proverb"), // 178
    Quote("Many a little makes a mickle.", "French proverb"), // 179
    Quote("There's no smoke without fire.", "French proverb"), // 180
    Quote("Nothing ventured, nothing gained.", "French proverb"), // 181
    Quote("Better late than never.", "French proverb"), // 182
    Quote("Unity is strength.", "French proverb"), // 183
    Quote("Everyone sees things from their own point of view.", "French proverb"), // 184
    Quote("Strike while the iron is hot.", "French proverb"), // 185
    Quote("Practice makes perfect.", "French proverb"), // 186
    Quote("Rome wasn't built in a day.", "French proverb"), // 187
    Quote("Short reckonings make long friends.", "French proverb"), // 188
    Quote("Nothing is impossible to a willing heart.", "French proverb"), // 189
    Quote("To will is to be able.", "French proverb"), // 190
    Quote("It's never too late to mend.", "French proverb"), // 191
    Quote("Sleep on it.", "French proverb"), // 192
    Quote("A rolling stone gathers no moss.", "French proverb"), // 193
    Quote("Birds of a feather flock together.", "French proverb"), // 194
    Quote("Never say never.", "French proverb"), // 195
    Quote("Out of sight, out of mind.", "French proverb"), // 196
    Quote("Appetite comes with eating.", "French proverb"), // 197
    Quote("A promise is a promise.", "French proverb"), // 198
    Quote("All things come to those who wait.", "French proverb"), // 199
    Quote("Think twice before you speak.", "French proverb"), // 200
    Quote("Speech is silver, silence is golden.", "French proverb"), // 201
    Quote("Walls have ears.", "French proverb"), // 202
    Quote("Other times, other manners.", "French proverb"), // 203
    Quote("Grasp all, lose all.", "French proverb"), // 204
    Quote("Sufficient unto the day is the evil thereof.", "French proverb"), // 205
    Quote("The first step is the hardest.", "French proverb"), // 206
    Quote("Don't teach your grandmother to suck eggs.", "French proverb"), // 207
    Quote("When the cat's away, the mice will play.", "French proverb"), // 208
    Quote("He who wills the end wills the means.", "French proverb"), // 209
    Quote("He who laughs last laughs best.", "French proverb"), // 210
    Quote("Prevention is better than cure.", "French proverb"), // 211
    Quote("If you want something done right, do it yourself.", "French proverb"), // 212
    Quote("Appearances can be deceptive.", "French proverb"), // 213
    Quote("Seek and ye shall find.", "French proverb"), // 214
    Quote("No one is bound to do the impossible.", "French proverb"), // 215
    Quote("A prophet is not without honour, save in his own country.", "French proverb"), // 216
    Quote("All in good time.", "French proverb"), // 217
    Quote("Forewarned is forearmed.", "French proverb"), // 218
    Quote("Great minds think alike.", "French proverb"), // 219
    Quote("Don't put all your eggs in one basket.", "French proverb"), // 220
    Quote("Ill-gotten gains never prosper.", "French proverb"), // 221
    Quote("The sun shines on everyone.", "French proverb"), // 222
    Quote("Ne'er cast a clout till May be out.", "French proverb"), // 223
    Quote("The more, the merrier.", "French proverb"), // 224
    Quote("Out of debt, out of danger.", "French proverb"), // 225
    Quote("Comparison is not proof.", "French proverb"), // 226
    Quote("Desperate times call for desperate measures.", "French proverb"), // 227
    Quote("Opportunity makes the thief.", "French proverb"), // 228
    Quote("Give time time.", "French proverb"), // 229
    Quote("There's no such thing as a menial job.", "French proverb"), // 230
    Quote("A hungry belly has no ears.", "French proverb"), // 231
    Quote("Grasp the nettle and it stings.", "French proverb"), // 232
    Quote("The game is not worth the candle.", "French proverb"), // 233
    Quote("Half a loaf is better than no bread.", "French proverb"), // 234
    Quote("Better alone than in bad company.", "French proverb"), // 235
    Quote("The early bird catches the worm.", "French proverb"), // 236
    Quote("Little by little, the bird builds its nest.", "French proverb"), // 237
    Quote("All's well that ends well.", "French proverb"), // 238
    Quote("As you make your bed, so you must lie on it.", "French proverb"), // 239
    Quote("Let sleeping dogs lie.", "French proverb"), // 240
    Quote("A friend in need is a friend indeed.", "French proverb"), // 241
    Quote("Advisers are not payers.", "French proverb"), // 242
    Quote("You can't make an omelette without breaking eggs.", "French proverb"), // 243
    Quote("Money can't buy happiness.", "French proverb"), // 244
    Quote("One man's loss is another man's gain.", "French proverb"), // 245
    Quote("No two days are alike.", "French proverb"), // 246
    Quote("If you really want to, you can.", "French proverb"), // 247
    Quote("All that glitters is not gold.", "French proverb"), // 248
    Quote("A good deed is never lost.", "French proverb"), // 249
    Quote("Out of the mouths of babes.", "French proverb"), // 250
    Quote("A tree is known by its fruit.", "French proverb"), // 251
    Quote("No rose without a thorn.", "French proverb"), // 252
    Quote("After the effort, the reward.", "French proverb"), // 253
    Quote("Nobody ever died of embarrassment.", "French proverb"), // 254
    Quote("Small gifts keep friendship alive.", "French proverb"), // 255
    Quote("Never put off till tomorrow what you can do today.", "French proverb"), // 256
    Quote("Tell me who your friends are, and I'll tell you who you are.", "French proverb"), // 257
    Quote("Once bitten, twice shy.", "French proverb"), // 258
    Quote("He who steals an egg will steal an ox.", "French proverb"), // 259
    Quote("What's done is done.", "French proverb"), // 260
    Quote("It's an ill wind that blows nobody any good.", "French proverb"), // 261
    Quote("Lost time is never found again.", "French proverb"), // 262
    Quote("The master's eye makes the horse fat.", "French proverb"), // 263
    Quote("Good tools make good workers.", "French proverb"), // 264
    Quote("It takes all sorts to make a world.", "French proverb"), // 265
    Quote("Still waters run deep.", "French proverb"), // 266
    Quote("Anger is a bad counsellor.", "French proverb"), // 267
    Quote("While there's life, there's hope.", "French proverb"), // 268
    Quote("In for a penny, in for a pound.", "French proverb"), // 269
    Quote("More haste, less speed.", "French proverb"), // 270
    Quote("One nail drives out another.", "French proverb"), // 271
    Quote("Better to be envied than pitied.", "French proverb"), // 272
    Quote("In the land of the blind, the one-eyed man is king.", "French proverb"), // 273
    Quote("Old habits die hard.", "French proverb"), // 274
    Quote("Better to keep what you have than to run after more.", "French proverb"), // 275
    Quote("Blood will tell.", "French proverb"), // 276
    Quote("Give a dog a bad name and hang him.", "French proverb"), // 277
    Quote("Easy in the morning, not too fast at night.", "French proverb"), // 278
    Quote("You can't have too much of a good thing.", "French proverb"), // 279
    Quote("He who sleeps forgets his hunger.", "French proverb"), // 280
    Quote("Time heals all wounds.", "French proverb"), // 281
    Quote("The absent are always in the wrong.", "French proverb"), // 282
    Quote("Spare the rod and spoil the child.", "French proverb"), // 283
    Quote("There's many a slip twixt cup and lip.", "French proverb"), // 284
    Quote("Silence gives consent.", "French proverb"), // 285
    Quote("Misfortunes never come singly.", "French proverb"), // 286
    Quote("Youth must have its fling.", "French proverb"), // 287
    Quote("Curiosity killed the cat.", "French proverb"), // 288
    Quote("Charity begins at home.", "French proverb"), // 289
    Quote("Christmas on the balcony, Easter by the fire.", "French proverb"), // 290
    Quote("Don't judge a book by its cover.", "French proverb"), // 291
    Quote("Tit for tat.", "French proverb"), // 292
    Quote("Move your feet, lose your seat.", "French proverb"), // 293
    Quote("Like father, like son.", "French proverb"), // 294
    Quote("Time is money.", "French proverb"), // 295
    Quote("A penny saved is a penny earned.", "French proverb"), // 296
    Quote("There's no use crying over spilt milk.", "French proverb"), // 297
    Quote("We learn from our mistakes.", "French proverb"), // 298
    Quote("He who fears leaves should not go into the woods.", "French proverb"), // 299
    Quote("A bad workman always blames his tools.", "French proverb"), // 300
    Quote("You can't have your cake and eat it.", "French proverb"), // 301
    Quote("You reap what you sow.", "French proverb"), // 302
    Quote("There's no place like home.", "French proverb"), // 303
    Quote("Idleness is the mother of all vices.", "French proverb"), // 304
    Quote("Happy people have no history.", "French proverb"), // 305
    Quote("Spoken words fly away, written words remain.", "Latin proverb"), // 306
    Quote("To err is human, to persist is diabolical.", "Latin proverb"), // 307
    Quote("While I breathe, I hope.", "Latin proverb"), // 308
    Quote("Habit is second nature.", "Latin proverb"), // 309
    Quote("Constant dripping wears away the stone.", "Latin proverb"), // 310
    Quote("Patience is the key to relief.", "Arabic proverb"), // 311
    Quote("Seek knowledge, even as far as China.", "Arabic proverb"), // 312
    Quote("He who wants to do something finds a way; he who doesn't finds an excuse.", "Arabic proverb"), // 313
    Quote("What is past has fled; what you hope for is absent; but the present is yours.", "Arabic proverb"), // 314
    Quote("Choose the neighbour before the house, the companion before the road.", "Arabic proverb"), // 315
    Quote("He who has health has hope, and he who has hope has everything.", "Arabic proverb"), // 316
    Quote("Time is like a sword: if you do not cut it, it cuts you.", "Arabic proverb"), // 317
    Quote("Learning in childhood is like engraving in stone.", "Arabic proverb"), // 318
    Quote("One hand cannot clap.", "Arabic proverb"), // 319
    Quote("Knowledge is light.", "Arabic proverb"), // 320
    Quote("The words of the night are erased by the day.", "Arabic proverb"), // 321
    Quote("He who is patient prevails.", "Arabic proverb"), // 322
    Quote("The best speech is brief and clear.", "Arabic proverb"), // 323
    Quote("A friend is known in times of hardship.", "Arabic proverb"), // 324
    Quote("Ask the experienced rather than the learned.", "Arabic proverb"), // 325
    Quote("He who digs a pit for his brother falls into it himself.", "Arabic proverb"), // 326
    Quote("Health is a crown that only the sick can see.", "Arabic proverb"), // 327
    Quote("Contentment is a treasure that never runs out.", "Arabic proverb"), // 328
    Quote("He who has tasted knows.", "Arabic proverb"), // 329
    Quote("The dogs bark, but the caravan moves on.", "Arabic proverb"), // 330
    Quote("The tongue has no bones, but it can break bones.", "Arabic proverb"), // 331
    Quote("He who sows good reaps good.", "Arabic proverb"), // 332
    Quote("An open book is a mind that speaks.", "Arabic proverb"), // 333
    Quote("Every delay hides a blessing.", "Moroccan proverb"), // 334
    Quote("He who wants honey must bear the bee stings.", "Moroccan proverb"), // 335
    Quote("Do good and forget it.", "Moroccan proverb"), // 336
    Quote("What's past is past.", "Moroccan proverb"), // 337
    Quote("Everyone pulls the embers towards their own bread.", "Moroccan proverb"), // 338
    Quote("Grain by grain, the granary fills up.", "Moroccan proverb"), // 339
    Quote("He who has no elder has no counsel.", "Moroccan proverb"), // 340
    Quote("He who keeps knocking at the door ends up getting in.", "Moroccan proverb"), // 341
    Quote("The camel does not see its own hump.", "Moroccan proverb"), // 342
    Quote("If you want to go fast, go alone; if you want to go far, go together.", "African proverb"), // 343
    Quote("No matter how long a log stays in the water, it will never become a crocodile.", "African proverb"), // 344
    Quote("A lie may bear flowers, but never fruit.", "African proverb"), // 345
    Quote("It takes a village to raise a child.", "African proverb"), // 346
    Quote("If you don't know where you are going, look back at where you came from.", "African proverb"), // 347
    Quote("He who asks questions never loses his way.", "African proverb"), // 348
    Quote("Rain does not fall on one roof alone.", "African proverb"), // 349
    Quote("Hot water does not forget that it was once cold.", "African proverb"), // 350
    Quote("A single finger cannot wash the face.", "African proverb"), // 351
    Quote("The river winds because no one shows it the way.", "African proverb"), // 352
    Quote("When elephants fight, it is the grass that suffers.", "African proverb"), // 353
    Quote("The new rope is woven at the end of the old one.", "African proverb"), // 354
    Quote("Do not mock the crocodile before you have crossed the river.", "African proverb"), // 355
    Quote("A tree falls the way it leans.", "African proverb"), // 356
    Quote("One log does not make a fire.", "African proverb"), // 357
    Quote("You cannot hide the sun with your hand.", "African proverb"), // 358
    Quote("Patience can cook a stone.", "African proverb"), // 359
    Quote("Teeth sometimes smile at those they are about to bite.", "African proverb"), // 360
    Quote("The lion does not turn around when a small dog barks.", "African proverb"), // 361
    Quote("What an old man sees sitting down, a young man cannot see standing up.", "African proverb"), // 362
    Quote("Words are like water: once spilled, they cannot be gathered up.", "African proverb"), // 363
    Quote("You cannot hide the smoke of a lit fire.", "African proverb"), // 364
    Quote("A falling tree makes more noise than a growing forest.", "African proverb"), // 365
    Quote("The desert teaches patience, the sea teaches humility.", "Tuareg proverb"), // 366
    Quote("He who has friends is richer than he who has gold.", "Tuareg proverb"), // 367
    Quote("Give a man a fish and you feed him for a day; teach a man to fish and you feed him for a lifetime.", "Chinese proverb"), // 368
    Quote("The best time to plant a tree was twenty years ago. The second best time is now.", "Chinese proverb"), // 369
    Quote("He who asks a question is a fool for five minutes; he who does not ask remains a fool forever.", "Chinese proverb"), // 370
    Quote("When you drink the water, remember the spring.", "Chinese proverb"), // 371
    Quote("It is better to light a candle than to curse the darkness.", "Chinese proverb"), // 372
    Quote("Seeing once is better than hearing a hundred times.", "Chinese proverb"), // 373
    Quote("Dripping water hollows out stone.", "Chinese proverb"), // 374
    Quote("Be not afraid of going slowly; be afraid only of standing still.", "Chinese proverb"), // 375
    Quote("The frog in the well knows nothing of the ocean.", "Chinese proverb"), // 376
    Quote("The man who moves a mountain begins by carrying away small stones.", "Chinese proverb"), // 377
    Quote("Learning is like rowing upstream: not to advance is to drop back.", "Chinese proverb"), // 378
    Quote("A good neighbour is better than a distant relative.", "Chinese proverb"), // 379
    Quote("When the winds of change blow, some build walls, others build windmills.", "Chinese proverb"), // 380
    Quote("Knowledge is a treasure that follows its owner everywhere.", "Chinese proverb"), // 381
    Quote("Great trees grow from small seeds.", "Chinese proverb"), // 382
    Quote("Teachers open the door, but you must enter by yourself.", "Chinese proverb"), // 383
    Quote("It takes ten years to grow a tree, and a hundred years to cultivate a person.", "Chinese proverb"), // 384
    Quote("He who stands on tiptoe does not stand firm.", "Chinese proverb"), // 385
    Quote("Running water never grows stale.", "Chinese proverb"), // 386
    Quote("A single spark can start a prairie fire.", "Chinese proverb"), // 387
    Quote("Before you embark on a journey of revenge, dig two graves.", "Chinese proverb"), // 388
    Quote("The bamboo that bends is stronger than the oak that resists.", "Japanese proverb"), // 389
    Quote("Fall seven times, stand up eight.", "Japanese proverb"), // 390
    Quote("Even monkeys fall from trees.", "Japanese proverb"), // 391
    Quote("The nail that sticks out gets hammered down.", "Japanese proverb"), // 392
    Quote("After victory, tighten your helmet strap.", "Japanese proverb"), // 393
    Quote("Sit on a stone for three years and it will grow warm.", "Japanese proverb"), // 394
    Quote("Even if the sky falls, there is a hole to escape through.", "Korean proverb"), // 395
    Quote("Patience is a tree whose root is bitter, but its fruit is very sweet.", "Persian proverb"), // 396
    Quote("The tree does not withhold its shade, even from the woodcutter.", "Indian proverb"), // 397
    Quote("The morning is wiser than the evening.", "Russian proverb"), // 398
    Quote("A cup of coffee commits one to forty years of friendship.", "Turkish proverb"), // 399
    Quote("He who goes slowly goes safely, and he who goes safely goes far.", "Italian proverb"), // 400
    Quote("A good laugh and a long sleep are the best cures in the doctor's book.", "Irish proverb"), // 401
    Quote("The fish rots from the head down.", "Greek proverb"), // 402
    Quote("A smooth sea never made a skilled sailor.", "English proverb"), // 403
    Quote("Every cloud has a silver lining.", "English proverb"), // 404
    Quote("An apple a day keeps the doctor away.", "English proverb"), // 405
    Quote("Where there's a will, there's a way.", "English proverb"), // 406
    Quote("Don't count your chickens before they hatch.", "English proverb"), // 407
    Quote("Actions speak louder than words.", "English proverb"), // 408
    Quote("A candle loses nothing by lighting another candle.", "Proverb"), // 409
    Quote("Even the longest night ends with the dawn.", "Proverb"), // 410
    Quote("It is not the mountain that is high, but the courage that is low.", "Proverb"), // 411
    Quote("Happiness cannot be bought, only shared.", "Proverb"), // 412
    Quote("Every morning is a blank page.", "Proverb"), // 413
    Quote("He who has planted a tree has not lived in vain.", "Proverb"), // 414
    Quote("Truth is like oil: it always rises to the surface.", "Proverb"), // 415
    Quote("A wise man learns from the mistakes of others; a fool, from his own.", "Proverb"), // 416
    Quote("Sometimes you must lose a battle to win the war.", "Proverb"), // 417
)
