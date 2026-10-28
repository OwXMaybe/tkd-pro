// UI strings (Turkish and English). Keys mirror the Android app where possible.
const STRINGS = {
  en: {
    app: 'TKD PRO', tagline: 'Personal Edition',
    nav_home: 'Home', nav_train: 'Train', nav_coach: 'AI Coach', nav_insights: 'Insights', nav_profile: 'Profile',
    nav_running: 'Running', nav_performance: 'Performance', nav_motion: 'Motion Lab', nav_settings: 'Settings',
    login_title: 'Sign in to TKD PRO', login_code_hint: 'Enter the 6-digit code shown on a device that is already signed in (Settings → Connect a device).',
    login_code: 'Pairing code', login_token: 'Owner token', login_use_token: 'Use the owner token instead',
    login_token_hint: 'The owner token is in owner-token.txt in the TKD PRO data folder on your computer.',
    login_action: 'Sign in', login_failed: 'Sign-in failed', login_rate: 'Too many attempts. Wait a few minutes.',
    loading: 'Loading…', offline: 'The computer running TKD PRO cannot be reached. Changes are not possible until it is back.',
    save: 'Save', cancel: 'Cancel', delete: 'Delete', edit: 'Edit', add: 'Add', back: 'Back', close: 'Close', confirm_delete: 'Delete this record? This also removes it from your phone.',
    empty: 'Nothing recorded yet.', saved: 'Saved', none: '—', yes: 'Yes', no: 'No', optional: 'optional', all: 'All',
    measured: 'Measured', estimated: 'Estimate', manual: 'Manual', ai_label: 'AI-generated',
    synced_from_phone: 'Synced with your phone',
    // Home
    home_week: 'This week', home_sessions: 'Sessions', home_minutes: 'Training minutes', home_load: 'Session load', home_load_hint: 'duration × RPE, subjective estimate',
    home_km: 'Running distance', home_recovery_today: 'Today\'s check-in', home_no_checkin: 'No check-in today.', home_checkin: 'Check in',
    home_recent: 'Recent activity', home_goals: 'Active goals', home_next_comp: 'Next competition', home_days_left: '{n} days left',
    home_hello: 'Hello, {name}', home_hello_anon: 'Hello',
    // Train
    train_sessions: 'Sessions', train_techniques: 'Techniques', train_combinations: 'Combinations', train_matches: 'Matches', train_competitions: 'Competitions',
    session_new: 'New session', session_edit: 'Edit session', session_name: 'Name', session_category: 'Category', session_status: 'Status',
    session_start: 'Date and time', session_duration: 'Duration (min)', session_location: 'Location', session_difficulty: 'Difficulty (1–5)',
    session_rpe: 'RPE (1–10)', session_notes: 'Notes', session_techniques: 'Techniques in this session', session_add_technique: 'Add technique',
    session_duplicate: 'A session with this name, category and start time already exists.',
    rec_technique: 'Technique', rec_sets: 'Sets', rec_reps: 'Total reps', rec_right: 'Right reps', rec_left: 'Left reps', rec_side: 'Side', rec_duration: 'Duration (s)',
    tech_new: 'New technique', tech_name: 'Name', tech_category: 'Category', tech_description: 'Description', tech_notes: 'Notes', tech_coach: 'Coach feedback',
    tech_builtin: 'Built-in', tech_usage: 'Used in {n} sessions · {r} reps',
    combo_new: 'New combination', combo_steps: 'Steps', combo_add_step: 'Add step', combo_practices: 'Practice log', combo_practice_add: 'Log practice', combo_reps: 'Repetitions',
    match_new: 'New match', match_competition: 'Competition', match_date: 'Date', match_opponent: 'Opponent', match_category: 'Category', match_stage: 'Stage',
    match_result: 'Result', match_score_for: 'Points for', match_score_against: 'Points against', match_strengths: 'Strengths', match_improvements: 'To improve',
    match_rounds: 'Rounds (manual observations)', match_round: 'Round {n}',
    round_attacks: 'Attack attempts', round_success: 'Successful attacks', round_defence: 'Defensive actions', round_counter: 'Counterattacks', round_tactical: 'Tactical notes',
    comp_new: 'New competition', comp_name: 'Name', comp_location: 'Location',
    // Running
    run_list: 'Runs', run_distance: 'Distance', run_time: 'Moving time', run_pace: 'Avg pace', run_elev: 'Elevation gain', run_hr: 'Avg heart rate',
    run_splits: 'Splits', run_best: 'Best efforts', run_record_on_phone: 'Runs are recorded with the phone\'s GPS. They appear here after syncing.',
    run_no_route: 'Route not synced yet.', run_map_offline: 'Map tiles unavailable — showing the route outline.', run_notes: 'Notes', run_hr_manual: 'Avg heart rate (manual)', run_maxhr_manual: 'Max heart rate (manual)',
    // Performance
    perf_reaction: 'Reaction tests', perf_tests: 'Performance tests', perf_recovery: 'Recovery', perf_reaction_phone: 'Reaction tests run on the phone (touch timing); results sync here.',
    perf_avg: 'Average', perf_best: 'Best', perf_valid: 'Valid trials', perf_device: 'Device', perf_new_metric: 'New result', perf_test: 'Test', perf_value: 'Value', perf_method: 'Method',
    perf_method_hint: 'How it was measured (e.g. tape measure, jump mat)',
    rec_new: 'Daily check-in', rec_day: 'Day', rec_sleep_h: 'Sleep (hours)', rec_sleep_q: 'Sleep quality', rec_energy: 'Energy', rec_fatigue: 'Fatigue', rec_soreness: 'Soreness',
    rec_recovery: 'Recovery', rec_rhr: 'Resting heart rate', rec_scale: '1 = low · 5 = high',
    // Insights
    ins_range: 'Period', ins_4w: '4 weeks', ins_12w: '12 weeks', ins_1y: '1 year',
    ins_training_minutes: 'Training minutes per week', ins_load: 'Session load per week (estimate)', ins_running_km: 'Running km per week',
    ins_categories: 'Minutes by category', ins_top_tech: 'Most practised techniques (reps)', ins_recovery: 'Daily recovery (1–5)', ins_reaction: 'Reaction average (ms)',
    ins_table: 'Show table', ins_chart: 'Show chart', ins_week: 'Week',
    // Coach
    coach_title: 'AI Coach', coach_disabled: 'The AI Coach is off. Turn it on and choose which data it may use in Settings → AI data permissions.',
    coach_unavailable: 'AI is not configured on the server (no OpenAI key). Add OPENAI_API_KEY to server.env and restart TKD PRO.',
    coach_placeholder: 'Ask about your training…', coach_send: 'Send', coach_sources: 'Based on: {s}', coach_failed: 'No answer. Try again.', coach_clear: 'Clear chat',
    coach_weekly: 'Weekly report', coach_weekly_make: 'Create weekly report', coach_disclaimer: 'AI answers can be wrong. They are not medical advice and never replace your coach.',
    coach_thinking: 'Thinking…',
    // Motion
    motion_title: 'Motion Lab', motion_hint: 'Video analyses made on the phone appear here. All values are 2D estimates from video, not measurements of force or speed.',
    motion_server: 'Analyse a video on this computer', motion_server_off: 'Server video analysis is not installed on this computer. Use the phone for on-device analysis.',
    motion_reps: 'Repetitions', motion_quality_fail: 'Video quality was not sufficient — no metrics were produced.', motion_ankle: 'Peak ankle height (torso lengths)',
    motion_knee: 'Kick knee angle at peak (°)', motion_support: 'Support knee angle at peak (°)', motion_trunk: 'Trunk lean at peak (°)', motion_ttp: 'Time to peak (ms)',
    motion_technique: 'Technique', motion_side: 'Side', motion_auto: 'Automatic', motion_upload: 'Analyse', motion_working: 'Analysing… this can take a minute.', motion_ai: 'AI interpretation',
    // Profile
    prof_title: 'Profile', prof_name: 'Display name', prof_started: 'Training since (year)', prof_birth: 'Birth year', prof_club: 'Club', prof_coach: 'Coach',
    prof_belt: 'Current belt', prof_weight: 'Weight category', prof_side: 'Dominant side', prof_belts: 'Belt history', prof_belt_new: 'Add belt', prof_goals: 'Goals', goal_new: 'New goal',
    goal_title: 'Title', goal_description: 'Description', goal_category: 'Category', goal_target_date: 'Target date', goal_target: 'Target value', goal_current: 'Current value',
    goal_unit: 'Unit', goal_status: 'Status',
    // Settings
    set_title: 'Settings', set_language: 'Language', set_theme: 'Theme', theme_system: 'System', theme_light: 'Light', theme_dark: 'Dark',
    set_connect: 'Connect a device', set_connect_desc: 'Pair your phone (TKD PRO app → Settings → Computer & phone) or sign in another browser with this code. The code works once and expires in 5 minutes.',
    set_connect_make: 'Show pairing code', set_connect_address: 'Address for the phone', set_connect_expires: 'Expires {t}',
    set_connect_wifi: 'The phone must be on the same Wi-Fi. If it cannot connect, allow TKD PRO in Windows Firewall for private networks.',
    set_devices: 'Signed-in devices', set_device_revoke: 'Sign out', set_device_this: 'this browser', set_device_last: 'last used {t}',
    set_ai: 'AI data permissions', set_ai_enable: 'Use the AI Coach on this computer', set_ai_desc: 'Only the categories you allow are sent. Name, club, coach, photos, videos and GPS routes are never sent.',
    set_server: 'Server', set_version: 'Version {v}', set_ai_configured: 'AI configured: {v}', set_motion_available: 'Server video analysis: {v}',
    set_logout: 'Sign out of this browser', set_install: 'Install as an app', set_install_desc: 'Install TKD PRO as a window on this computer or on your phone\'s home screen.',
    ai_training: 'Training', ai_running: 'Running', ai_matches: 'Matches', ai_motion: 'Motion analyses', ai_recovery: 'Recovery', ai_reaction: 'Reaction & performance tests', ai_profile: 'Profile (belt, years, goals — no name)',
  },
  tr: {
    app: 'TKD PRO', tagline: 'Kişisel Sürüm',
    nav_home: 'Ana Sayfa', nav_train: 'Antrenman', nav_coach: 'AI Koç', nav_insights: 'Analiz', nav_profile: 'Profil',
    nav_running: 'Koşu', nav_performance: 'Performans', nav_motion: 'Hareket Lab', nav_settings: 'Ayarlar',
    login_title: 'TKD PRO\'ya giriş', login_code_hint: 'Zaten giriş yapılmış bir cihazda gösterilen 6 haneli kodu girin (Ayarlar → Cihaz bağla).',
    login_code: 'Eşleştirme kodu', login_token: 'Sahip anahtarı', login_use_token: 'Bunun yerine sahip anahtarını kullan',
    login_token_hint: 'Sahip anahtarı, bilgisayarınızdaki TKD PRO veri klasöründe owner-token.txt dosyasındadır.',
    login_action: 'Giriş yap', login_failed: 'Giriş başarısız', login_rate: 'Çok fazla deneme. Birkaç dakika bekleyin.',
    loading: 'Yükleniyor…', offline: 'TKD PRO\'nun çalıştığı bilgisayara ulaşılamıyor. Geri gelene kadar değişiklik yapılamaz.',
    save: 'Kaydet', cancel: 'Vazgeç', delete: 'Sil', edit: 'Düzenle', add: 'Ekle', back: 'Geri', close: 'Kapat', confirm_delete: 'Bu kayıt silinsin mi? Telefonunuzdan da kaldırılır.',
    empty: 'Henüz kayıt yok.', saved: 'Kaydedildi', none: '—', yes: 'Evet', no: 'Hayır', optional: 'isteğe bağlı', all: 'Tümü',
    measured: 'Ölçülen', estimated: 'Tahmin', manual: 'Manuel', ai_label: 'AI üretimi',
    synced_from_phone: 'Telefonunuzla eşitlenir',
    home_week: 'Bu hafta', home_sessions: 'Antrenman', home_minutes: 'Antrenman dakikası', home_load: 'Antrenman yükü', home_load_hint: 'süre × RPE, öznel tahmin',
    home_km: 'Koşu mesafesi', home_recovery_today: 'Bugünkü durum', home_no_checkin: 'Bugün için durum girilmedi.', home_checkin: 'Durum gir',
    home_recent: 'Son etkinlikler', home_goals: 'Aktif hedefler', home_next_comp: 'Sıradaki müsabaka', home_days_left: '{n} gün kaldı',
    home_hello: 'Merhaba, {name}', home_hello_anon: 'Merhaba',
    train_sessions: 'Antrenmanlar', train_techniques: 'Teknikler', train_combinations: 'Kombinasyonlar', train_matches: 'Maçlar', train_competitions: 'Müsabakalar',
    session_new: 'Yeni antrenman', session_edit: 'Antrenmanı düzenle', session_name: 'Ad', session_category: 'Kategori', session_status: 'Durum',
    session_start: 'Tarih ve saat', session_duration: 'Süre (dk)', session_location: 'Yer', session_difficulty: 'Zorluk (1–5)',
    session_rpe: 'RPE (1–10)', session_notes: 'Notlar', session_techniques: 'Bu antrenmandaki teknikler', session_add_technique: 'Teknik ekle',
    session_duplicate: 'Aynı ad, kategori ve başlangıç saatine sahip bir antrenman zaten var.',
    rec_technique: 'Teknik', rec_sets: 'Set', rec_reps: 'Toplam tekrar', rec_right: 'Sağ tekrar', rec_left: 'Sol tekrar', rec_side: 'Taraf', rec_duration: 'Süre (sn)',
    tech_new: 'Yeni teknik', tech_name: 'Ad', tech_category: 'Kategori', tech_description: 'Açıklama', tech_notes: 'Notlar', tech_coach: 'Antrenör geri bildirimi',
    tech_builtin: 'Hazır', tech_usage: '{n} antrenmanda · {r} tekrar',
    combo_new: 'Yeni kombinasyon', combo_steps: 'Adımlar', combo_add_step: 'Adım ekle', combo_practices: 'Çalışma kaydı', combo_practice_add: 'Çalışma ekle', combo_reps: 'Tekrar',
    match_new: 'Yeni maç', match_competition: 'Müsabaka', match_date: 'Tarih', match_opponent: 'Rakip', match_category: 'Kategori', match_stage: 'Tur',
    match_result: 'Sonuç', match_score_for: 'Alınan puan', match_score_against: 'Verilen puan', match_strengths: 'Güçlü yönler', match_improvements: 'Geliştirilecekler',
    match_rounds: 'Raundlar (manuel gözlem)', match_round: '{n}. raunt',
    round_attacks: 'Atak denemesi', round_success: 'Başarılı atak', round_defence: 'Savunma hareketi', round_counter: 'Kontra atak', round_tactical: 'Taktik notlar',
    comp_new: 'Yeni müsabaka', comp_name: 'Ad', comp_location: 'Yer',
    run_list: 'Koşular', run_distance: 'Mesafe', run_time: 'Hareket süresi', run_pace: 'Ort. tempo', run_elev: 'Tırmanış', run_hr: 'Ort. nabız',
    run_splits: 'Bölümler', run_best: 'En iyi dereceler', run_record_on_phone: 'Koşular telefonun GPS\'iyle kaydedilir. Eşitlemeden sonra burada görünür.',
    run_no_route: 'Rota henüz eşitlenmedi.', run_map_offline: 'Harita yüklenemedi — rotanın çizimi gösteriliyor.', run_notes: 'Notlar', run_hr_manual: 'Ort. nabız (manuel)', run_maxhr_manual: 'Maks. nabız (manuel)',
    perf_reaction: 'Reaksiyon testleri', perf_tests: 'Performans testleri', perf_recovery: 'Toparlanma', perf_reaction_phone: 'Reaksiyon testleri telefonda (dokunma zamanlaması) yapılır; sonuçlar buraya eşitlenir.',
    perf_avg: 'Ortalama', perf_best: 'En iyi', perf_valid: 'Geçerli deneme', perf_device: 'Cihaz', perf_new_metric: 'Yeni sonuç', perf_test: 'Test', perf_value: 'Değer', perf_method: 'Yöntem',
    perf_method_hint: 'Nasıl ölçüldü (ör. mezura, sıçrama matı)',
    rec_new: 'Günlük durum', rec_day: 'Gün', rec_sleep_h: 'Uyku (saat)', rec_sleep_q: 'Uyku kalitesi', rec_energy: 'Enerji', rec_fatigue: 'Yorgunluk', rec_soreness: 'Kas ağrısı',
    rec_recovery: 'Toparlanma', rec_rhr: 'Dinlenik nabız', rec_scale: '1 = düşük · 5 = yüksek',
    ins_range: 'Dönem', ins_4w: '4 hafta', ins_12w: '12 hafta', ins_1y: '1 yıl',
    ins_training_minutes: 'Haftalık antrenman dakikası', ins_load: 'Haftalık antrenman yükü (tahmin)', ins_running_km: 'Haftalık koşu km',
    ins_categories: 'Kategoriye göre dakika', ins_top_tech: 'En çok çalışılan teknikler (tekrar)', ins_recovery: 'Günlük toparlanma (1–5)', ins_reaction: 'Reaksiyon ortalaması (ms)',
    ins_table: 'Tabloyu göster', ins_chart: 'Grafiği göster', ins_week: 'Hafta',
    coach_title: 'AI Koç', coach_disabled: 'AI Koç kapalı. Ayarlar → AI veri izinleri\'nden açın ve kullanabileceği verileri seçin.',
    coach_unavailable: 'Sunucuda AI yapılandırılmamış (OpenAI anahtarı yok). server.env dosyasına OPENAI_API_KEY ekleyip TKD PRO\'yu yeniden başlatın.',
    coach_placeholder: 'Antrenmanınla ilgili sor…', coach_send: 'Gönder', coach_sources: 'Kaynak: {s}', coach_failed: 'Yanıt alınamadı. Tekrar deneyin.', coach_clear: 'Sohbeti temizle',
    coach_weekly: 'Haftalık rapor', coach_weekly_make: 'Haftalık rapor oluştur', coach_disclaimer: 'AI yanıtları hatalı olabilir. Tıbbi tavsiye değildir ve antrenörünüzün yerini tutmaz.',
    coach_thinking: 'Düşünüyor…',
    motion_title: 'Hareket Lab', motion_hint: 'Telefonda yapılan video analizleri burada görünür. Tüm değerler videodan 2B tahmindir; kuvvet veya hız ölçümü değildir.',
    motion_server: 'Bu bilgisayarda video analiz et', motion_server_off: 'Bu bilgisayarda sunucu video analizi kurulu değil. Cihaz üzerinde analiz için telefonu kullanın.',
    motion_reps: 'Tekrar', motion_quality_fail: 'Video kalitesi yeterli değildi — metrik üretilmedi.', motion_ankle: 'Zirvede ayak bileği yüksekliği (gövde boyu)',
    motion_knee: 'Zirvede vuruş dizi açısı (°)', motion_support: 'Zirvede destek dizi açısı (°)', motion_trunk: 'Zirvede gövde eğimi (°)', motion_ttp: 'Zirveye süre (ms)',
    motion_technique: 'Teknik', motion_side: 'Taraf', motion_auto: 'Otomatik', motion_upload: 'Analiz et', motion_working: 'Analiz ediliyor… bir dakika sürebilir.', motion_ai: 'AI yorumu',
    prof_title: 'Profil', prof_name: 'Görünen ad', prof_started: 'Başlangıç yılı', prof_birth: 'Doğum yılı', prof_club: 'Kulüp', prof_coach: 'Antrenör',
    prof_belt: 'Mevcut kuşak', prof_weight: 'Kilo kategorisi', prof_side: 'Baskın taraf', prof_belts: 'Kuşak geçmişi', prof_belt_new: 'Kuşak ekle', prof_goals: 'Hedefler', goal_new: 'Yeni hedef',
    goal_title: 'Başlık', goal_description: 'Açıklama', goal_category: 'Kategori', goal_target_date: 'Hedef tarih', goal_target: 'Hedef değer', goal_current: 'Mevcut değer',
    goal_unit: 'Birim', goal_status: 'Durum',
    set_title: 'Ayarlar', set_language: 'Dil', set_theme: 'Tema', theme_system: 'Sistem', theme_light: 'Açık', theme_dark: 'Koyu',
    set_connect: 'Cihaz bağla', set_connect_desc: 'Telefonunuzu (TKD PRO uygulaması → Ayarlar → Bilgisayar ve telefon) eşleştirin veya başka bir tarayıcıda bu kodla giriş yapın. Kod bir kez çalışır ve 5 dakikada geçersiz olur.',
    set_connect_make: 'Eşleştirme kodunu göster', set_connect_address: 'Telefon için adres', set_connect_expires: 'Geçerlilik: {t}',
    set_connect_wifi: 'Telefon aynı Wi-Fi\'da olmalı. Bağlanamıyorsa Windows Güvenlik Duvarı\'nda TKD PRO\'ya özel ağlar için izin verin.',
    set_devices: 'Giriş yapmış cihazlar', set_device_revoke: 'Çıkış yaptır', set_device_this: 'bu tarayıcı', set_device_last: 'son kullanım {t}',
    set_ai: 'AI veri izinleri', set_ai_enable: 'Bu bilgisayarda AI Koç\'u kullan', set_ai_desc: 'Yalnızca izin verdiğiniz kategoriler gönderilir. Ad, kulüp, antrenör, fotoğraf, video ve GPS rotaları asla gönderilmez.',
    set_server: 'Sunucu', set_version: 'Sürüm {v}', set_ai_configured: 'AI yapılandırılmış: {v}', set_motion_available: 'Sunucu video analizi: {v}',
    set_logout: 'Bu tarayıcıdan çıkış yap', set_install: 'Uygulama olarak yükle', set_install_desc: 'TKD PRO\'yu bu bilgisayarda ayrı bir pencere veya telefonunuzun ana ekranına uygulama olarak yükleyin.',
    ai_training: 'Antrenman', ai_running: 'Koşu', ai_matches: 'Maçlar', ai_motion: 'Hareket analizleri', ai_recovery: 'Toparlanma', ai_reaction: 'Reaksiyon ve performans testleri', ai_profile: 'Profil (kuşak, yıl, hedefler — ad yok)',
  },
};

// Enum labels shared with the Android app's enum names.
const ENUMS = {
  en: {
    TrainingCategory: { TECHNICAL: 'Technical', TACTICAL: 'Tactical', SPARRING: 'Sparring', FOOTWORK: 'Footwork', REACTION: 'Reaction', COMBINATION: 'Combination', CONDITIONING: 'Conditioning', STRENGTH_POWER: 'Strength & power', MOBILITY: 'Mobility', RECOVERY: 'Recovery' },
    SessionStatus: { PLANNED: 'Planned', COMPLETED: 'Completed' },
    BodySide: { RIGHT: 'Right', LEFT: 'Left', BOTH: 'Both' },
    TechniqueCategory: { KICK: 'Kick', PUNCH: 'Punch', FOOTWORK: 'Footwork', BLOCK: 'Block', OTHER: 'Other' },
    MatchResult: { WIN: 'Win', LOSS: 'Loss', DRAW: 'Draw', NOT_RECORDED: 'Not recorded' },
    RunType: { EASY: 'Easy', ENDURANCE: 'Endurance', TEMPO: 'Tempo', INTERVAL: 'Interval', RECOVERY: 'Recovery', SPRINT: 'Sprint', CUSTOM: 'Custom' },
    ReactionTestType: { VISUAL: 'Visual', AUDIO: 'Audio', RANDOM: 'Random', REPEATED: 'Repeated' },
    PerformanceTestType: { SINGLE_LEG_BALANCE: 'Single-leg balance', SINGLE_LEG_BALANCE_EYES_CLOSED: 'Single-leg balance, eyes closed', TAPPING_COORDINATION: 'Tapping coordination', SIT_AND_REACH: 'Sit and reach', FRONT_SPLIT: 'Front split', SIDE_SPLIT: 'Side split', STANDING_LONG_JUMP: 'Standing long jump', VERTICAL_JUMP: 'Vertical jump', HEXAGON_AGILITY: 'Hexagon agility' },
    GoalCategory: { TECHNICAL: 'Technical', COMPETITION: 'Competition', RUNNING: 'Running', FITNESS: 'Fitness', BELT: 'Belt', OTHER: 'Other' },
    GoalStatus: { ACTIVE: 'Active', ACHIEVED: 'Achieved', ARCHIVED: 'Archived' },
    AnalysisStatus: { COMPLETED: 'Completed', INSUFFICIENT_QUALITY: 'Insufficient quality', FAILED: 'Failed' },
  },
  tr: {
    TrainingCategory: { TECHNICAL: 'Teknik', TACTICAL: 'Taktik', SPARRING: 'Maç çalışması', FOOTWORK: 'Ayak çalışması', REACTION: 'Reaksiyon', COMBINATION: 'Kombinasyon', CONDITIONING: 'Kondisyon', STRENGTH_POWER: 'Kuvvet ve güç', MOBILITY: 'Mobilite', RECOVERY: 'Toparlanma' },
    SessionStatus: { PLANNED: 'Planlandı', COMPLETED: 'Tamamlandı' },
    BodySide: { RIGHT: 'Sağ', LEFT: 'Sol', BOTH: 'İkisi' },
    TechniqueCategory: { KICK: 'Tekme', PUNCH: 'Yumruk', FOOTWORK: 'Ayak çalışması', BLOCK: 'Blok', OTHER: 'Diğer' },
    MatchResult: { WIN: 'Galibiyet', LOSS: 'Mağlubiyet', DRAW: 'Beraberlik', NOT_RECORDED: 'Kaydedilmedi' },
    RunType: { EASY: 'Hafif', ENDURANCE: 'Dayanıklılık', TEMPO: 'Tempo', INTERVAL: 'İnterval', RECOVERY: 'Toparlanma', SPRINT: 'Sprint', CUSTOM: 'Özel' },
    ReactionTestType: { VISUAL: 'Görsel', AUDIO: 'İşitsel', RANDOM: 'Rastgele', REPEATED: 'Tekrarlı' },
    PerformanceTestType: { SINGLE_LEG_BALANCE: 'Tek ayak denge', SINGLE_LEG_BALANCE_EYES_CLOSED: 'Tek ayak denge, gözler kapalı', TAPPING_COORDINATION: 'Dokunma koordinasyonu', SIT_AND_REACH: 'Otur-uzan', FRONT_SPLIT: 'Ön split', SIDE_SPLIT: 'Yan split', STANDING_LONG_JUMP: 'Durarak uzun atlama', VERTICAL_JUMP: 'Dikey sıçrama', HEXAGON_AGILITY: 'Altıgen çeviklik' },
    GoalCategory: { TECHNICAL: 'Teknik', COMPETITION: 'Müsabaka', RUNNING: 'Koşu', FITNESS: 'Kondisyon', BELT: 'Kuşak', OTHER: 'Diğer' },
    GoalStatus: { ACTIVE: 'Aktif', ACHIEVED: 'Ulaşıldı', ARCHIVED: 'Arşiv' },
    AnalysisStatus: { COMPLETED: 'Tamamlandı', INSUFFICIENT_QUALITY: 'Yetersiz kalite', FAILED: 'Başarısız' },
  },
};

// Performance test units and direction, mirroring PerformanceTestType in the app.
export const PERF_TESTS = {
  SINGLE_LEG_BALANCE: ['s', true], SINGLE_LEG_BALANCE_EYES_CLOSED: ['s', true], TAPPING_COORDINATION: ['taps', true],
  SIT_AND_REACH: ['cm', true], FRONT_SPLIT: ['cm', false], SIDE_SPLIT: ['cm', false],
  STANDING_LONG_JUMP: ['cm', true], VERTICAL_JUMP: ['cm', true], HEXAGON_AGILITY: ['s', false],
};

function initialLang() {
  try {
    const saved = localStorage.getItem('tkd.lang');
    if (saved === 'tr' || saved === 'en') return saved;
  } catch { /* storage unavailable */ }
  return (navigator.language || 'en').toLowerCase().startsWith('tr') ? 'tr' : 'en';
}

export let lang = initialLang();

export function setLang(value) {
  lang = value;
  try { localStorage.setItem('tkd.lang', value); } catch { /* ignore */ }
  document.documentElement.lang = value;
}

export function t(key, vars) {
  let s = STRINGS[lang][key] ?? STRINGS.en[key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replaceAll(`{${k}}`, v);
  return s;
}

export function enumLabel(type, value) {
  if (value == null) return t('none');
  return ENUMS[lang][type]?.[value] ?? ENUMS.en[type]?.[value] ?? value;
}

export function enumValues(type) {
  return Object.keys(ENUMS.en[type]);
}
