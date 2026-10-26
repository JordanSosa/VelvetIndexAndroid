// Velvet Index — tag taxonomy.
// Booru sites (e621, Rule34, Gelbooru, Danbooru, Konachan, yande.re) share this
// underscore tag vocabulary. Edit freely; nothing here is enforced, it's all suggestions.
//
// rule of thumb:
//   * animation / drawn  -> e621, Rule34, Gelbooru, Danbooru
//   * real people        -> X, Reddit, Coomer, Kemono, the tube sites
//   * anything Japanese  -> JAVLibrary for the code, then the tube sites for the file

const TAXONOMY = [
  { id: 'dark', label: 'Dark / Non-con / Mind-break', open: true,
    note: 'Where boorus beat tube sites completely. Tube sites tag none of this.',
    tags: ['rape','non-con','forced','reluctant','mind_break','mind_control','hypnosis','drugged','aphrodisiac',
           'sleeping','unconscious','somophilia','bondage','restrained','clothing_torn','gangbang',
           'impregnation','tentacles','oviposition','vore','unbirth','deflation','chikan','netorare'] },

  { id: 'zoo', label: 'Zoo / Bestiality',
    note: 'e621 and Gelbooru have the deepest coverage. Add "animated" for motion.',
    tags: ['bestiality','zoophilia','feral','feral_sex','animal_penis','animal_genitalia','canine','equine',
           'dragon','knotting','mating_press','tie','dog','horse','multiple_feral','size_difference','tentacles'] },

  { id: 'leaks', label: 'Leaks / Real / Amateur',
    note: 'Real people. Coomer (OnlyFans) and Kemono (Patreon) are the leak archives.',
    tags: ['amateur','real','leaked','onlyfans','patreon','homemade','hidden_cam','voyeur','creampie','cumshot',
           'girlfriend','wife','milf','webcam'] },

  { id: 'teen', label: 'Teen / Petite / Loli',
    note: '"loli" is a body-type tag, not an age. For "teen" as a real age, use petite / flat_chest.',
    tags: ['loli','shota','petite','flat_chest','small_breasts','child','age_difference','shortstack','teen','tomboy'] },

  { id: 'milf', label: 'MILF / Mature / GILF',
    tags: ['milf','mature_female','older_female','gilf','old','granny','hag','motherly','huge_breasts'] },

  { id: 'incest', label: 'Incest / Step-',
    tags: ['incest','mother_and_son','father_and_daughter','brother_and_sister','stepmother','stepsister',
           'stepfather','stepbrother','aunt','uncle','cousin','twincest','sister'] },

  { id: 'futa', label: 'Futanari / Trans / Intersex',
    tags: ['futanari','gynomorph','andromorph','herm','dickgirl','transgender','intersex','futa_with_female',
           'futa_with_male','futa_on_futa','newhalf'] },

  { id: 'furry', label: 'Furry / Anthro',
    tags: ['anthro','feral','scalie','dragon','canine','feline','equine','pokemon','digimon','sonic',
           'undertale','avian','lagomorph','hyena','otter','kobold'] },

  { id: 'bdsm', label: 'BDSM / Bondage',
    tags: ['bdsm','bondage','rope','shibari','suspension','blindfold','gag','collar','leash','spanking',
           'whipping','wax_play','electro','chastity','mummification','cage','anal_hook','nipple_clamps'] },

  { id: 'femdom', label: 'Femdom / Domination / Pegging',
    tags: ['femdom','pegging','facesitting','foot_domination','strap-on','orgasm_denial','tease_and_denial',
           'mistress','slave','petplay','body_worship','smothering','ball_busting'] },

  { id: 'body', label: 'Body / Hyper / Expansion',
    tags: ['hyper','hyper_breasts','hyper_cock','hyper_hips','expansion','inflation','breast_expansion',
           'muscular','abs','thick_thighs','bbw','ssbbw','chubby','huge_breasts','huge_penis'] },

  { id: 'preg', label: 'Pregnant / Lactation / Breeding',
    tags: ['pregnant','pregnancy','lactation','breastfeeding','hyperlactation','breeding','cum_inflation',
           'egg_laying','clutch','impregnation','belly_bulge'] },

  { id: 'feet', label: 'Feet / Armpit / Body detail',
    tags: ['feet','footjob','soles','toes','anklet','armpit','navel','thighs','legs','ass','small_hips'] },

  { id: 'oral', label: 'Oral / Anal / Deep',
    tags: ['oral','blowjob','deepthroat','irrumatio','cunnilingus','anilingus','rimjob','anal','double_penetration',
           'triple_penetration','ass_to_mouth','fisting','gape','prolapse','anal_beads'] },

  { id: 'cum', label: 'Cum / Bukkake / Excessive',
    tags: ['bukkake','facial','cum_in_mouth','cum_on_face','creampie','cum_inflation','excessive_cum',
           'cum_on_breasts','cum_bath','cum_swap','deepthroat_creampie'] },

  { id: 'monster', label: 'Monster / Creature / Alien',
    tags: ['monster','tentacles','orc','goblin','slime','eldritch','insect','alien','demon','angel','giant',
           'size_difference','macro','micro'] },

  { id: 'group', label: 'Group / Orgy / Harem',
    tags: ['group_sex','threesome','foursome','orgy','gangbang','spitroast','harem','reverse_harem',
           'bukkake','double_penetration','multiple_partners'] },

  { id: 'ntr', label: 'NTR / Cuckold / Cheating',
    tags: ['netorare','ntr','cuckold','cheating','netorase','hotwife','swinging','wife_watching',
           'netori','netorare_vs_netorase'] },

  { id: 'fdom', label: 'Femme / Yaoi / Yuri / Bara',
    tags: ['yuri','yaoi','boys_love','girls_love','bara','shounen_ai','shoujo_ai','femslash','slash'] },

  { id: 'cosplay', label: 'Cosplay / Uniform / Latex',
    tags: ['cosplay','pantyhose','stockings','lingerie','latex','leotard','school_uniform','maid','bunny_girl',
           'kimono','gym_uniform','nurse','corset'] },

  { id: 'watersports', label: 'Watersports / Omorashi',
    tags: ['watersports','peeing','golden_shower','omorashi','diaper','desperation','wetting'] },

  { id: 'solo', label: 'Solo / Toys / Orgasm',
    tags: ['solo','masturbation','dildo','vibrator','fingering','orgasm','multiple_orgasms','squirting',
           'edging','toy_play'] },

  { id: 'public', label: 'Public / Exhibitionism / Voyeur',
    tags: ['public','exhibitionism','outdoors','voyeur','flash','streaking','hidden_cam','glory_hole','cruising'] },

  { id: 'anime', label: 'Anime / Characters / Series',
    note: 'Swap in whatever franchise you want; boorus tag every one of them.',
    tags: ['genshin_impact','honkai','league_of_legends','overwatch','pokemon','my_hero_academia',
           'naruto','one_piece','neon_genesis_evangelion','fate_(series)','touhou','hololive'] },

  { id: 'quality', label: 'Quality / Meta filters',
    note: 'Append these to any search above. e621 & Danbooru only.',
    tags: ['rating:e','rating:q','score:>50','order:score','order:random','animated','sound',
           'highres','absurd_res','animated_gif','animated_png','webm'] }
];

const RATINGS = [
  { id: 'all',        label: 'Any rating' },
  { id: 'explicit',   label: 'Explicit' },
  { id: 'questionable', label: 'Questionable' },
  { id: 'safe',       label: 'Safe' }
];
