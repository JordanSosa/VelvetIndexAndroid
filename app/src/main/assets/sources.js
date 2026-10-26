// Velvet Index — source adapters.
// Each adapter is: { id, label, group, kind, url(query, page), headers, parse(json) }.
// parse() returns raw rows; index.html normalises them. Adding a site is ~10 lines.

const UA = 'VelvetIndex/1.0 (Android; +https://github.com/JordanSosa/VelvetIndexAndroid)';
const enc = encodeURIComponent;

function pref(k) { try { return Native.getPref(k) || ''; } catch (e) { return ''; } }

function creds() {
  const k = pref('gelbooru_key'), u = pref('gelbooru_user');
  return (k && u) ? '&api_key=' + enc(k) + '&user_id=' + enc(u) : '';
}

const SOURCES = [
  {
    id: 'e621', label: 'e621', group: 'Booru', enabled: true,
    note: 'Furry + western. Deepest tag coverage anywhere. Rate limited to ~2/s.',
    headers: { 'User-Agent': UA, 'Accept': 'application/json' },
    url: (q, page) => 'https://e621.net/posts.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q ? q + ' order:id_desc' : 'order:id_desc'),
    parse: j => (j.posts || []).map(p => ({
      id: p.id,
      page_url: 'https://e621.net/posts/' + p.id,
      file_url: p.file && p.file.url,
      preview_url: (p.preview && p.preview.url) || (p.sample && p.sample.url),
      title: 'e621 #' + p.id,
      rating: p.rating,
      score: (p.score && p.score.total) || 0,
      media_type: /webm|mp4|swf/i.test((p.file && p.file.ext) || '') ? 'video' : 'image',
      tags: ['general', 'species', 'character', 'copyright', 'artist', 'meta']
              .flatMap(k => (p.tags && p.tags[k]) || []).join(' ')
    }))
  },
  {
    id: 'rule34', label: 'Rule34', group: 'Booru', enabled: true,
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid='
                    + page + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://rule34.xxx/index.php?page=post&s=view&id=' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url || p.sample_url,
      title: 'Rule34 #' + p.id,
      score: parseInt(p.score, 10) || 0,
      media_type: /\.(mp4|webm)(\?|$)/i.test(p.file_url || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'gelbooru', label: 'Gelbooru', group: 'Booru', enabled: true,
    note: 'Add api_key + user_id in Settings if you get 401s.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid='
                    + page + '&tags=' + enc(q || 'all') + creds(),
    parse: j => ((j && j.post) || []).map(p => ({
      id: p.id,
      page_url: 'https://gelbooru.com/index.php?page=post&s=view&id=' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url,
      title: 'Gelbooru #' + p.id,
      rating: p.rating,
      score: parseInt(p.score, 10) || 0,
      media_type: /\.(mp4|webm)(\?|$)/i.test(p.file_url || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'danbooru', label: 'Danbooru', group: 'Booru', enabled: false,
    note: 'Best anime tag vocabulary. Anonymous access is capped at 2 tags.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://danbooru.donmai.us/posts.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://danbooru.donmai.us/posts/' + p.id,
      file_url: p.file_url || p.large_file_url,
      preview_url: p.preview_file_url,
      title: 'Danbooru #' + p.id,
      rating: p.rating,
      score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image',
      tags: p.tag_string
    }))
  },
  {
    id: 'konachan', label: 'Konachan', group: 'Booru', enabled: false,
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://konachan.com/post.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://konachan.com/post/show/' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url,
      title: 'Konachan #' + p.id,
      rating: p.rating,
      score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'yandere', label: 'yande.re', group: 'Booru', enabled: false,
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://yande.re/post.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://yande.re/post/show/' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url,
      title: 'yande.re #' + p.id,
      rating: p.rating,
      score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'coomer', label: 'Coomer (OnlyFans leaks)', group: 'Real / Leaks', enabled: true,
    note: 'Real creators. Search is fuzzy; use the web search link for exact handles.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://coomer.su/api/v1/onlyfans/search?q=' + enc(q) + '&o=' + (page * 50),
    parse: j => ((j && (j.posts || j.results)) || []).map(p => {
      const f = (p.attachments && p.attachments[0]) || {};
      return {
        id: p.id,
        page_url: 'https://coomer.su/onlyfans/user/' + (p.user || '') + '/post/' + p.id,
        file_url: f.path ? 'https://coomer.su/data' + f.path : '',
        preview_url: f.path ? 'https://coomer.su/data' + f.path : (p.file && p.file.thumb),
        title: (p.user || 'coomer') + ' - ' + (p.title || p.id),
        media_type: f.name && /\.(mp4|webm|mov)$/i.test(f.name) ? 'video' : 'image',
        tags: 'coomer onlyfans ' + (p.user || '')
      };
    })
  },
  {
    id: 'kemono', label: 'Kemono (Patreon leaks)', group: 'Real / Leaks', enabled: true,
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://kemono.su/api/v1/patreon/search?q=' + enc(q) + '&o=' + (page * 50),
    parse: j => ((j && (j.posts || j.results)) || []).map(p => {
      const f = (p.attachments && p.attachments[0]) || {};
      return {
        id: p.id,
        page_url: 'https://kemono.su/patreon/user/' + (p.user || '') + '/post/' + p.id,
        file_url: f.path ? 'https://kemono.su/data' + f.path : '',
        preview_url: f.path ? 'https://kemono.su/data' + f.path : '',
        title: (p.user || 'kemono') + ' - ' + (p.title || p.id),
        media_type: f.name && /\.(mp4|webm|mov)$/i.test(f.name) ? 'video' : 'image',
        tags: 'kemono patreon ' + (p.user || '')
      };
    })
  },
  {
    id: 'reddit', label: 'Reddit', group: 'Real / Leaks', enabled: false,
    note: 'Often rate limited. Great for r/nsfw411-style rabbit holes.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://www.reddit.com/search.json?q=' + enc(q)
                    + '&include_over_18=on&sort=top&t=all&limit=50&after=' + (page * 50),
    parse: j => (((j || {}).data || {}).children || []).map(c => {
      const d = c.data || {};
      return {
        id: d.id,
        page_url: 'https://reddit.com' + (d.permalink || ''),
        file_url: d.url_overridden_by_dest || d.url,
        preview_url: (d.preview && d.preview.images && d.preview.images[0]
                      && d.preview.images[0].source && d.preview.images[0].source.url) || '',
        title: d.title || 'Reddit post',
        score: d.score || 0,
        media_type: /\.(mp4|webm|gif)$/i.test(d.url || '') ? 'video' : 'image',
        tags: 'reddit ' + (d.subreddit || '')
      };
    })
  }
];

const VIDEO_SOURCES = [
  { id: 'pornhub', label: 'Pornhub' },
  { id: 'xvideos', label: 'XVideos' },
  { id: 'xhamster', label: 'xHamster' },
  { id: 'xnxx', label: 'XNXX' },
  { id: 'redtube', label: 'RedTube' },
  { id: 'youporn', label: 'YouPorn' },
  { id: 'eporner', label: 'Eporner' },
  { id: 'txxx', label: 'TXXX' }
];

const LINK_SOURCES = [
  { label: 'Pornhub',              url: q => 'https://www.pornhub.com/video/search?search=' + enc(q) },
  { label: 'XVideos',              url: q => 'https://www.xvideos.com/?k=' + enc(q) },
  { label: 'xHamster',             url: q => 'https://xhamster.com/search/' + enc(q) },
  { label: 'XNXX',                 url: q => 'https://www.xnxx.com/?k=' + enc(q) },
  { label: 'Noodle Magazine',      url: q => 'https://noodlemagazine.com/video/' + enc(q) },
  { label: 'JAVLibrary (by code)', url: q => 'https://www.javlibrary.com/en/vl_searchbyid.php?keyword=' + enc(q) },
  { label: 'JAV site search',      url: q => 'https://www.google.com/search?q=' + enc('site:javlibrary.com OR site:jav.guru OR site:javdb.com ' + q) },
  { label: 'IAFD (performers)',    url: q => 'https://www.google.com/search?q=' + enc('site:iafd.com ' + q) },
  { label: 'Coomer web search',    url: q => 'https://coomer.su/search?q=' + enc(q) },
  { label: 'Kemono web search',    url: q => 'https://kemono.su/search?q=' + enc(q) },
  { label: 'X / Twitter media',    url: q => 'https://x.com/search?q=' + enc(q + ' filter:media') },
  { label: 'Reddit NSFW',          url: q => 'https://www.reddit.com/search/?q=' + enc(q) + '&type=link&include_over_18=on' },
  { label: 'scrolller',            url: q => 'https://scrolller.com/r/' + enc(q) },
  { label: 'nHentai',              url: q => 'https://nhentai.net/search/?q=' + enc(q) },
  { label: 'e-hentai',             url: q => 'https://e-hentai.org/?f_search=' + enc(q) },
  { label: 'Hitomi',               url: q => 'https://hitomi.la/search.html?q=' + enc(q) },
  { label: 'Hanime',               url: q => 'https://hanime.tv/search?q=' + enc(q) },
  { label: 'Sukebei (torrents)',   url: q => 'https://sukebei.nyaa.si/?q=' + enc(q) },
  { label: 'F95Zone (games)',      url: q => 'https://www.google.com/search?q=' + enc('site:f95zone.to ' + q) },
  { label: 'ManyVids',             url: q => 'https://www.manyvids.com/Store/Search?searchString=' + enc(q) },
  { label: 'PornPics',             url: q => 'https://www.pornpics.com/?q=' + enc(q) },
  { label: 'Fapello',              url: q => 'https://fapello.com/search/?q=' + enc(q) }
];
