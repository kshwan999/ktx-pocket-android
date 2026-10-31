package kr.personal.ktxpocket;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final int INK = Color.rgb(40, 41, 36), MUTED = Color.rgb(107, 108, 100), ACCENT = Color.rgb(187, 73, 43);
    private final int PAPER = Color.rgb(247, 245, 241), SOFT = Color.rgb(243, 240, 234), LINE = Color.rgb(230, 225, 217);
    private Fold loginFold, alternativeFold, routeFold;
    private boolean wasActive;
    private TextView statusPill, footerHint;
    private LinearLayout statusCard;
    private ProgressBar activityProgress;
    private Button pay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<View> inputs = new ArrayList<>();
    private EditText username, password, departure, arrival;
    private TextView state, detail, attempts;
    private Button dateButton, fromButton, toButton, start, stop;
    private Spinner people, seat;
    private LinearLayout content;
    private LinearLayout alternativeList;
    private TextView recentNote;
    private Button dismissReminder;
    private final ArrayList<JSONObject> alternatives = new ArrayList<>();
    private final ArrayList<View> alternativeButtons = new ArrayList<>();
    private final String[] seatLabels = {"일반실만", "일반실 우선 · 없으면 특실", "특실만", "특실 우선 · 없으면 일반실"};
    private CredentialStore credentialStore;
    private CheckBox rememberId, rememberPassword;
    private TextView credentialNote;
    private Button forgetCredentials;
    private boolean applyingCredentials;
    private LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    private LocalTime from = LocalTime.of(9, 0), to = LocalTime.of(18, 0);
    private final Runnable refresh = new Runnable() { public void run() { renderStatus(); handler.postDelayed(this, 1000); } };
    private final String[] options = {"GENERAL_ONLY", "GENERAL_FIRST", "SPECIAL_ONLY", "SPECIAL_FIRST"};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        SearchAlerts.createChannel(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout frame = vertical(); frame.setBackgroundColor(PAPER);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = vertical(); page.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(page); frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame); content = page;

        LinearLayout brand = horizontal();
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_train); logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView name = text("KTX Pocket", 20, INK); name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); name.setPadding(dp(10), 0, 0, 0);
        brand.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("내 휴대폰에서", 11, MUTED); brand.addView(local); add(brand, 0);
        TextView headline = text("표 찾기는 맡겨두세요.", 26, INK); headline.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); headline.setLetterSpacing(-.035f); add(headline, 25);
        add(text("조건만 정하고, 알림으로 확인하세요.", 14, MUTED), 6);

        statusCard = card(page, 22); content = statusCard;
        LinearLayout statusTop = horizontal();
        statusTop.addView(text("내 검색", 12, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        statusPill = text("대기", 11, MUTED); statusPill.setPadding(dp(10), dp(5), dp(10), dp(5)); statusPill.setBackground(background(SOFT, 8)); statusTop.addView(statusPill); add(statusTop, 0);
        state = text("검색 대기", 22, INK); state.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(state, 14);
        detail = text("예약 조건을 입력하고 검색을 시작해주세요.", 14, MUTED); add(detail, 7);
        activityProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); activityProgress.setIndeterminate(true); activityProgress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams progressSize = new LinearLayout.LayoutParams(-1, dp(4)); progressSize.topMargin = dp(16); statusCard.addView(activityProgress, progressSize); activityProgress.setVisibility(View.GONE);
        attempts = text("휴대폰 단독 실행 · PC 연결 불필요", 11, MUTED); add(attempts, 14);
        pay = button("코레일톡에서 예약 · 결제 확인", 1); pay.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.korail.talk");
            if (launch != null) startActivity(launch); else toast("코레일톡 앱을 설치한 뒤 예약내역을 확인해주세요.");
        }); add(pay, 14); pay.setVisibility(View.GONE);
        dismissReminder = button("결제·취소 확인 · 재알림 끄기", 2); dismissReminder.setOnClickListener(v -> {
            SearchAlerts.dismissPayment(this); renderStatus(); toast("결제 재알림을 껐습니다. 실제 결제·취소는 코레일톡에서 확인해주세요.");
        }); add(dismissReminder, 4); dismissReminder.setVisibility(View.GONE);

        routeFold = fold(page, "어디로 떠나세요?", "출발·도착과 날짜, 시간을 정해주세요.", true); content = routeFold.body;
        LinearLayout routeCard = content, route = horizontal();
        LinearLayout left = vertical(), right = vertical();
        left.setBackground(background(SOFT, 16)); right.setBackground(background(SOFT, 16));
        left.setPadding(dp(12), dp(11), dp(12), dp(5)); right.setPadding(dp(12), dp(11), dp(12), dp(5));
        route.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton swap = new ImageButton(this); swap.setImageResource(R.drawable.ic_swap); swap.setContentDescription("출발역과 도착역 바꾸기");
        swap.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1fbb492b), background(Color.TRANSPARENT, 24), background(Color.WHITE, 24)));
        swap.setOnClickListener(v -> { String previous = departure.getText().toString(); departure.setText(arrival.getText()); arrival.setText(previous); });
        route.addView(swap, new LinearLayout.LayoutParams(dp(48), dp(48))); inputs.add(swap);
        route.addView(right, new LinearLayout.LayoutParams(0, -2, 1)); add(route, 18);
        content = left; departure = station("출발역", "서울");
        content = right; arrival = station("도착역", "부산"); content = routeCard;
        dateButton = button("", 3); dateButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        dateButton.setOnClickListener(v -> new DatePickerDialog(this, (picker, y, m, d) -> { date = LocalDate.of(y, m+1, d); updateTimes(); }, date.getYear(), date.getMonthValue()-1, date.getDayOfMonth()).show()); add(dateButton, 14); inputs.add(dateButton);
        LinearLayout timeRow = horizontal();
        fromButton = button("", 3); fromButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); fromButton.setOnClickListener(v -> chooseTime(true));
        toButton = button("", 3); toButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); toButton.setOnClickListener(v -> chooseTime(false));
        timeRow.addView(fromButton, new LinearLayout.LayoutParams(0, -2, 1));
        Space timeSpace = new Space(this); timeRow.addView(timeSpace, new LinearLayout.LayoutParams(dp(10), 1));
        timeRow.addView(toButton, new LinearLayout.LayoutParams(0, -2, 1)); add(timeRow, 10); inputs.add(fromButton); inputs.add(toButton); updateTimes();
        add(text("종료 시간 이전에 출발하는 열차를 찾아요. 한국시간 기준입니다.", 11, MUTED), 8);
        add(text("탑승 인원", 12, MUTED), 18); people = spinner(new String[]{"어른 1명", "어른 2명", "어른 3명", "어른 4명", "어른 5명", "어른 6명"}); people.setContentDescription("탑승 인원 선택");
        add(text("좌석 종류", 12, MUTED), 14); seat = spinner(seatLabels); seat.setContentDescription("좌석 종류 선택");
        add(text("여러 명은 함께 예약하며, 인접 좌석은 보장되지 않아요.", 11, MUTED), 8);
        recentNote = text("검색을 시작하면 이 조건을 기억해요.", 11, MUTED); add(recentNote, 12);

        content = page; alternativeFold = fold(page, "다른 시간도 괜찮다면", "현재 조건에 대안을 더해 최대 3개를 찾아요.", false); content = alternativeFold.body;
        add(text("하나가 예약되면 모든 조건의 검색을 종료합니다.", 12, MUTED), 8);
        control("+  지금 조건을 대안으로 추가", () -> {
            try {
                JSONObject current = currentCondition(); checkCondition(current);
                for (JSONObject other : alternatives) if (sameCondition(current, other)) { toast("이미 추가한 조건입니다."); return; }
                if (alternatives.size() >= 2) { toast("대안은 2개까지 추가할 수 있습니다. 현재 입력 조건까지 최대 3개를 검색합니다."); return; }
                alternatives.add(current); renderAlternatives();
                toast("목록에 추가했습니다. 이제 입력란을 다른 날짜나 시간으로 바꿔주세요.");
            } catch (Exception error) { toast("역 이름과 날짜·시간을 확인해주세요."); }
        });
        alternativeList = vertical(); add(alternativeList, 14);

        content = page; loginFold = fold(page, "코레일 계정", "로그인 정보와 기억 설정", true); content = loginFold.body;
        username = field("회원번호 · 이메일 · 휴대폰 번호", InputType.TYPE_CLASS_TEXT); username.setSaveEnabled(false);
        password = field("코레일 비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setSaveEnabled(false);
        setupCredentialMemory(); loginFold.expand(username.getText().length() == 0 || password.getText().length() == 0);
        restoreConditions(); renderAlternatives();

        content = page; Fold settings = fold(page, "알림과 앱 설정", "한 번 설정하고, 알림으로 확인하세요.", false); content = settings.body;
        settingControl("예약 결과 알림 설정", () -> startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, "result")));
        settingControl("결제 재알림 시간 설정", () -> {
            if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + getPackageName())));
            } else toast("정확한 알람 권한이 허용되어 있습니다. 절전 설정에 따라 알림이 늦어질 수 있습니다.");
        });
        add(text("예약 후 약 1분, 결제 기한 약 5분 전에 다시 알려드립니다. 결제·취소 후에는 재알림을 꺼주세요. 알람 권한·절전·강제 종료 상태에 따라 알림이 늦거나 오지 않을 수 있습니다.", 12, MUTED), 10);
        settingControl("배터리 최적화 설정", () -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        control("기억한 검색 조건 지우기", () -> {
            getSharedPreferences("recent_search", 0).edit().clear().apply(); alternatives.clear(); renderAlternatives();
            recentNote.setText("저장된 조건을 지웠어요. 현재 입력값은 이번 검색에 사용할 수 있어요.");
        });
        add(text("요청 사이 3~5초 대기 · 집 Wi-Fi 사용 권장\n한 번에 최대 5시간 45분 검색합니다. 안드로이드나 절전 설정으로 더 일찍 중단될 수 있으며 강제 종료하면 검색도 끝납니다.", 12, MUTED), 14);
        settingControl("사용 라이브러리 및 이용 조건", () -> about());
        content = page;
        TextView legal = text("비공식 개인용 앱 · 결제는 코레일톡에서 직접", 11, MUTED); legal.setGravity(Gravity.CENTER); add(legal, 20);

        LinearLayout dock = vertical(); dock.setPadding(dp(20), dp(12), dp(20), dp(12)); dock.setBackgroundColor(Color.WHITE);
        View divider = new View(this); divider.setBackgroundColor(LINE); frame.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        footerHint = text("조건에 맞는 예약 1건만 확보해요.", 11, MUTED); footerHint.setGravity(Gravity.CENTER); dock.addView(footerHint);
        LinearLayout actions = horizontal();
        start = button("조건 확인하고 검색 시작", 0); start.setOnClickListener(v -> confirm()); actions.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
        stop = button("중지", 1); stop.setOnClickListener(v -> { if (SearchService.running) startService(new Intent(this, SearchService.class).setAction(SearchService.STOP)); });
        LinearLayout.LayoutParams stopSize = new LinearLayout.LayoutParams(dp(88), -2); stopSize.leftMargin = dp(10); actions.addView(stop, stopSize); stop.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-1, -2); actionSize.topMargin = dp(8); dock.addView(actions, actionSize); frame.addView(dock);
    }

    private void confirm() {
        if (SearchService.running) return;
        if (SearchAlerts.paymentPending(this)) { toast("이전 예약의 결제·취소를 확인한 뒤 재알림을 꺼주세요."); return; }
        if (username.getText().toString().trim().isEmpty() || password.getText().length() == 0) { loginFold.expand(true); (username.getText().length() == 0 ? username : password).requestFocus(); toast("로그인 정보를 입력해주세요."); return; }
        JSONArray conditions;
        StringBuilder summary = new StringBuilder();
        try {
            conditions = selectedConditions();
            for (int i = 0; i < conditions.length(); i++) {
                JSONObject condition = conditions.getJSONObject(i); checkCondition(condition);
                summary.append(i + 1).append(". ").append(describeCondition(condition)).append("\n\n");
            }
        } catch (Exception error) { toast("현재 조건과 추가한 대안의 역·날짜·시간을 확인해주세요. 지난 조건은 수정하거나 삭제해주세요."); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            toast("예약 성공 알림을 위해 알림 권한을 허용한 뒤 다시 시작해주세요."); return;
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (!notificationManager.areNotificationsEnabled() || notificationManager.getNotificationChannel("result").getImportance() == NotificationManager.IMPORTANCE_NONE) {
            toast("예약 결과 알림을 켠 뒤 검색을 시작해주세요.");
            startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, "result"));
            return;
        }
        new AlertDialog.Builder(this).setTitle("이 조건으로 자동 예약할까요?")
            .setMessage(summary + "위 조건 중 먼저 예약되는 1건만 확보하고 모든 검색을 종료합니다. 결제는 직접 해야 합니다. 개인용 이용에 한하며, 조회 간격을 줄일 수 없습니다.")
            .setNegativeButton("돌아가기", null).setPositiveButton("확인하고 검색 시작", (dialog, which) -> begin()).show();
    }

    private void begin() {
        try {
            JSONObject config = new JSONObject().put("username", username.getText().toString().trim()).put("password", password.getText().toString())
                .put("conditions", selectedConditions()).put("confirmed", true);
            startForegroundService(new Intent(this, SearchService.class).putExtra("config", config.toString()));
            getSharedPreferences("recent_search", 0).edit().putString("conditions", selectedConditions().toString()).apply();
            recentNote.setText("이번 검색 조건을 기억했어요. 다음 실행 때 불러옵니다.");
            try {
                credentialStore.save(username.getText().toString().trim(), password.getText().toString(), rememberId.isChecked(), rememberPassword.isChecked());
                credentialNote.setText(rememberPassword.isChecked() ? "아이디와 비밀번호를 이 휴대폰에 암호화해 기억합니다." : rememberId.isChecked() ? "아이디만 기억합니다. 비밀번호는 저장하지 않습니다." : "로그인 정보를 기억하지 않습니다.");
            } catch (Exception error) { credentialNote.setText("로그인 정보를 저장하지 못했습니다. 검색은 계속 진행합니다."); }
            forgetCredentials.setEnabled(credentialStore.hasSaved());
            if (!rememberPassword.isChecked()) password.setText("");
            start.setEnabled(false);
        } catch (Exception error) { toast("검색 서비스를 시작하지 못했습니다. 앱을 열어둔 상태에서 다시 시도해주세요."); }
    }

    private void setupCredentialMemory() {
        credentialStore = new CredentialStore(this);
        rememberId = new CheckBox(this); rememberId.setText("아이디 기억"); styleCheck(rememberId); add(rememberId, 4); inputs.add(rememberId);
        rememberPassword = new CheckBox(this); rememberPassword.setText("비밀번호도 기억"); styleCheck(rememberPassword); add(rememberPassword, 0); inputs.add(rememberPassword);
        credentialNote = text("선택한 정보는 검색을 시작할 때 기억하며, 다음 실행에 자동 입력됩니다.", 12, MUTED); add(credentialNote, 4);
        forgetCredentials = button("저장된 로그인 정보 지우기", 2); add(forgetCredentials, 6);
        applyingCredentials = true;
        try {
            CredentialStore.Saved saved = credentialStore.load();
            username.setText(saved.username); password.setText(saved.password);
            rememberId.setChecked(!saved.username.isEmpty()); rememberPassword.setChecked(!saved.password.isEmpty());
            if (!saved.password.isEmpty()) credentialNote.setText("이 휴대폰에 암호화해 둔 로그인 정보를 불러왔습니다.");
            else if (!saved.username.isEmpty()) credentialNote.setText("저장된 아이디를 불러왔습니다. 비밀번호는 입력해주세요.");
        } catch (Exception error) {
            try { credentialStore.clear(); } catch (Exception ignored) { }
            credentialNote.setText("저장된 정보를 열지 못했습니다. 로그인 정보를 다시 입력해주세요.");
        }
        applyingCredentials = false;
        forgetCredentials.setEnabled(credentialStore.hasSaved());
        rememberId.setOnCheckedChangeListener((button, checked) -> {
            if (applyingCredentials || checked) return;
            applyingCredentials = true; rememberPassword.setChecked(false); applyingCredentials = false;
            try { credentialStore.clear(); credentialNote.setText("기억을 해제하고 저장된 로그인 정보를 지웠습니다."); }
            catch (Exception error) { credentialNote.setText("저장된 정보 삭제를 완료하지 못했습니다. 지우기 버튼으로 다시 시도해주세요."); }
            forgetCredentials.setEnabled(credentialStore.hasSaved());
        });
        rememberPassword.setOnCheckedChangeListener((button, checked) -> {
            if (applyingCredentials) return;
            if (checked) {
                rememberId.setChecked(true);
                credentialNote.setText("검색을 시작하면 아이디와 비밀번호를 이 휴대폰에 암호화해 기억합니다.");
            } else {
                try { credentialStore.forgetPassword(); credentialNote.setText("저장된 비밀번호를 지웠습니다. 현재 입력한 값은 이번 검색에만 사용합니다."); }
                catch (Exception error) { credentialNote.setText("저장된 비밀번호 삭제를 완료하지 못했습니다. 지우기 버튼으로 다시 시도해주세요."); }
                forgetCredentials.setEnabled(credentialStore.hasSaved());
            }
        });
        forgetCredentials.setOnClickListener(view -> {
            try {
                credentialStore.clear();
                applyingCredentials = true;
                rememberId.setChecked(false); rememberPassword.setChecked(false);
                username.setText(""); password.setText("");
                applyingCredentials = false;
                credentialNote.setText(SearchService.running ? "저장된 정보를 지웠습니다. 이미 시작한 검색은 계속 진행합니다." : "저장된 로그인 정보를 지웠습니다.");
                forgetCredentials.setEnabled(false);
            } catch (Exception error) { credentialNote.setText("삭제를 완료하지 못했습니다. 다시 시도해주세요."); }
        });
    }

    private void renderStatus() {
        SharedPreferences prefs = getSharedPreferences(SearchService.PREFS, 0);
        String kind = prefs.getString("kind", "idle");
        String message = prefs.getString("message", "예약 조건을 입력하고 검색을 시작해주세요.");
        boolean active = SearchService.running;
        if (!active && (kind.equals("login") || kind.equals("searching") || kind.equals("reserving") || kind.equals("stopping"))) {
            kind = "stopped"; message = "검색 프로세스가 종료되었습니다. 처리 중이던 예약이 있다면 코레일 예약내역을 먼저 확인해주세요.";
        }
        String label = switch (kind) {
            case "login" -> "로그인 중";
            case "searching" -> "좌석을 찾고 있어요";
            case "reserving" -> "예약 결과 확인 중";
            case "stopping" -> "검색을 마무리하고 있어요";
            case "success" -> "예약 성공 · 결제해주세요";
            case "error", "uncertain" -> "확인이 필요해요";
            case "stopped" -> "검색 종료";
            default -> "검색 대기";
        };
        boolean needsAttention = kind.equals("error") || kind.equals("uncertain");
        boolean pending = SearchAlerts.paymentPending(this);
        if (active && !wasActive) { routeFold.expand(false); loginFold.expand(false); alternativeFold.expand(false); }
        if (!active && wasActive) routeFold.expand(true);
        wasActive = active;
        try {
            String caption = routeFold.body.getVisibility() == View.VISIBLE ? "출발·도착과 날짜, 시간을 정해주세요."
                : describeCondition(currentCondition()) + "\n총 " + selectedConditions().length() + "개 조건";
            if (!routeFold.description.getText().toString().equals(caption)) routeFold.description.setText(caption);
        } catch (Exception ignored) { }
        if (!state.getText().toString().equals(label)) state.setText(label);
        state.setTextColor(needsAttention ? Color.rgb(158, 56, 40) : INK);
        if (!detail.getText().toString().equals(message)) detail.setText(message);
        statusPill.setText(active ? "진행 중" : pending || kind.equals("success") ? "예약 완료" : needsAttention ? "확인 필요" : kind.equals("stopped") ? "종료" : "대기");
        statusPill.setTextColor(active || pending ? ACCENT : needsAttention ? Color.rgb(158, 56, 40) : MUTED);
        statusPill.setBackground(background(active || pending || needsAttention ? Color.rgb(253, 242, 237) : SOFT, 8));
        activityProgress.setVisibility(active ? View.VISIBLE : View.GONE);
        int count = prefs.getInt("attempts", 0);
        attempts.setText(count == 0 ? "휴대폰 단독 실행 · PC 연결 불필요" : count + "회 확인 · 요청 사이 3~5초 대기");
        start.setText(active ? "좌석을 찾고 있어요" : pending ? "결제 확인이 필요해요" : "조건 확인하고 검색 시작");
        start.setEnabled(!active && !pending); stop.setEnabled(active); stop.setVisibility(active ? View.VISIBLE : View.GONE);
        footerHint.setText(pending ? "코레일톡에서 결제·취소 후 재알림을 꺼주세요." : active ? "예약되면 알림으로 알려드려요." : "조건에 맞는 예약 1건만 확보해요.");
        pay.setVisibility(pending || kind.equals("success") ? View.VISIBLE : View.GONE);
        dismissReminder.setEnabled(pending); dismissReminder.setVisibility(pending ? View.VISIBLE : View.GONE);
        for (View input : inputs) { input.setEnabled(!active); input.setAlpha(active ? .55f : 1f); }
    }

    private JSONObject currentCondition() throws Exception {
        return new JSONObject().put("departure", departure.getText().toString().trim()).put("arrival", arrival.getText().toString().trim())
            .put("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)).put("start", from.format(DateTimeFormatter.ofPattern("HHmm")))
            .put("end", to.format(DateTimeFormatter.ofPattern("HHmm"))).put("passengers", people.getSelectedItemPosition()+1)
            .put("option", options[seat.getSelectedItemPosition()]);
    }

    private JSONArray selectedConditions() throws Exception {
        JSONArray result = new JSONArray().put(currentCondition());
        for (JSONObject other : alternatives) if (!sameCondition(result.getJSONObject(0), other)) result.put(other);
        return result;
    }

    private boolean sameCondition(JSONObject first, JSONObject second) throws Exception {
        for (String key : new String[]{"departure", "arrival", "date", "start", "end", "passengers", "option"})
            if (!first.get(key).equals(second.get(key))) return false;
        return true;
    }

    private void checkCondition(JSONObject value) throws Exception {
        String dep = value.getString("departure"), arr = value.getString("arrival");
        LocalDate day = LocalDate.parse(value.getString("date"), DateTimeFormatter.BASIC_ISO_DATE);
        LocalTime begin = LocalTime.parse(value.getString("start"), DateTimeFormatter.ofPattern("HHmm"));
        LocalTime end = LocalTime.parse(value.getString("end"), DateTimeFormatter.ofPattern("HHmm"));
        if (dep.trim().isEmpty() || arr.trim().isEmpty() || dep.equals(arr) || !end.isAfter(begin)
            || !ZonedDateTime.of(day, end, ZoneId.of("Asia/Seoul")).isAfter(ZonedDateTime.now(ZoneId.of("Asia/Seoul")))
            || value.getInt("passengers") < 1 || value.getInt("passengers") > 6 || optionIndex(value.getString("option")) < 0)
            throw new IllegalArgumentException();
    }

    private int optionIndex(String option) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(option)) return i;
        return -1;
    }

    private String describeCondition(JSONObject value) throws Exception {
        return value.getString("departure") + " → " + value.getString("arrival") + " · "
            + LocalDate.parse(value.getString("date"), DateTimeFormatter.BASIC_ISO_DATE) + "\n"
            + LocalTime.parse(value.getString("start"), DateTimeFormatter.ofPattern("HHmm")) + " ~ "
            + LocalTime.parse(value.getString("end"), DateTimeFormatter.ofPattern("HHmm")) + " 이전 · 어른 "
            + value.getInt("passengers") + "명 · " + seatLabels[optionIndex(value.getString("option"))];
    }

    private void renderAlternatives() {
        for (View view : alternativeButtons) inputs.remove(view);
        alternativeButtons.clear(); alternativeList.removeAllViews();
        if (alternatives.isEmpty()) {
            TextView empty = text("추가한 대안이 없어요.\n다른 날짜나 시간도 괜찮다면 추가해보세요.", 13, MUTED);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14)); empty.setBackground(background(SOFT, 14)); alternativeList.addView(empty);
        }
        for (int i = 0; i < alternatives.size(); i++) {
            JSONObject value = alternatives.get(i);
            try {
                LinearLayout item = vertical(); item.setPadding(dp(14), dp(14), dp(14), dp(4)); item.setBackground(background(SOFT, 14));
                TextView number = text("대안 " + (i + 1), 11, ACCENT); item.addView(number);
                TextView label = text(describeCondition(value), 13, INK); label.setPadding(0, dp(6), 0, dp(4)); item.addView(label);
                Button remove = button("이 대안 삭제", 2); remove.setOnClickListener(v -> { alternatives.remove(value); renderAlternatives(); });
                item.addView(remove); LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.topMargin = i == 0 ? 0 : dp(10); alternativeList.addView(item, size);
                inputs.add(remove); alternativeButtons.add(remove);
            } catch (Exception ignored) { }
        }
        if (!alternatives.isEmpty()) alternativeFold.expand(true);
    }

    private void restoreConditions() {
        String stored = getSharedPreferences("recent_search", 0).getString("conditions", null);
        if (stored == null) return;
        try {
            JSONArray saved = new JSONArray(stored);
            if (saved.length() < 1 || saved.length() > 3) throw new IllegalArgumentException();
            JSONObject first = saved.getJSONObject(0);
            departure.setText(first.getString("departure")); arrival.setText(first.getString("arrival"));
            date = LocalDate.parse(first.getString("date"), DateTimeFormatter.BASIC_ISO_DATE);
            from = LocalTime.parse(first.getString("start"), DateTimeFormatter.ofPattern("HHmm"));
            to = LocalTime.parse(first.getString("end"), DateTimeFormatter.ofPattern("HHmm"));
            int count = first.getInt("passengers"), option = optionIndex(first.getString("option"));
            if (count < 1 || count > 6 || option < 0) throw new IllegalArgumentException();
            people.setSelection(count - 1); seat.setSelection(option); updateTimes();
            for (int i = 1; i < saved.length(); i++) {
                JSONObject value = saved.getJSONObject(i), clean = new JSONObject();
                for (String key : new String[]{"departure", "arrival", "date", "start", "end", "passengers", "option"}) clean.put(key, value.get(key));
                alternatives.add(clean);
            }
            renderAlternatives();
            recentNote.setText("최근 조건을 불러왔어요. 지난 날짜와 대안은 수정해주세요.");
        } catch (Exception error) {
            alternatives.clear(); renderAlternatives();
            getSharedPreferences("recent_search", 0).edit().clear().apply();
            recentNote.setText("저장된 조건을 읽지 못했습니다. 조건을 다시 확인해주세요.");
        }
    }

    private void about() {
        try (java.io.InputStream stream = getAssets().open("NOTICE.txt")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String notice = bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            TextView view = text(notice, 12, INK); view.setPadding(dp(20), dp(16), dp(20), dp(16));
            ScrollView scroll = new ScrollView(this); scroll.addView(view);
            new AlertDialog.Builder(this).setTitle("사용 라이브러리 및 이용 조건").setView(scroll).setPositiveButton("닫기", null).show();
        } catch (Exception error) { toast("이용 조건 파일을 읽지 못했습니다."); }
    }
    private void chooseTime(boolean first) { LocalTime current = first ? from : to; new TimePickerDialog(this, (picker,h,m) -> { if (first) from=LocalTime.of(h,m); else to=LocalTime.of(h,m); updateTimes(); },current.getHour(),current.getMinute(),true).show(); }
    private void updateTimes() {
        controlValue(dateButton, "출발 날짜", date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)));
        controlValue(fromButton, "시작 시간", from.toString());
        controlValue(toButton, "종료 시간", to + " 이전");
    }

    private void controlValue(Button button, String label, String value) {
        SpannableString copy = new SpannableString(label + "\n" + value);
        copy.setSpan(new RelativeSizeSpan(12f / 14f), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        copy.setSpan(new ForegroundColorSpan(MUTED), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        copy.setSpan(new RelativeSizeSpan(17f / 14f), label.length() + 1, copy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(copy); button.setMinHeight(dp(76));
    }

    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }

    private LinearLayout card(LinearLayout parent, int gap) {
        LinearLayout view = vertical(); view.setPadding(dp(18), dp(18), dp(18), dp(18)); view.setBackground(background(Color.WHITE, 24));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.topMargin = dp(gap); parent.addView(view, size); return view;
    }

    private final class Fold {
        final Button header;
        final LinearLayout body;
        final TextView description;
        final String title;
        Fold(Button header, LinearLayout body, TextView description, String title) { this.header = header; this.body = body; this.description = description; this.title = title; }
        void expand(boolean expanded) {
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, 0);
            header.setContentDescription(title + (expanded ? ", 펼쳐짐. 접기" : ", 접힘. 펼치기"));
        }
    }

    private Fold fold(LinearLayout parent, String title, String caption, boolean expanded) {
        LinearLayout shell = card(parent, 14);
        Button header = button(title, 2); header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); header.setPadding(0, 0, 0, 0); shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(caption, 12, MUTED); shell.addView(description);
        LinearLayout body = vertical(); shell.addView(body, new LinearLayout.LayoutParams(-1, -2));
        Fold fold = new Fold(header, body, description, title); fold.expand(expanded); header.setOnClickListener(v -> fold.expand(body.getVisibility() != View.VISIBLE)); return fold;
    }

    private Button button(String label, int kind) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(14); view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setMinHeight(dp(52)); view.setMinimumWidth(0); view.setMinWidth(0); view.setPadding(dp(16), dp(12), dp(16), dp(12)); view.setLetterSpacing(0); view.setStateListAnimator(null);
        int fill = kind == 0 ? ACCENT : kind == 1 ? Color.rgb(253, 242, 237) : kind == 3 ? SOFT : Color.TRANSPARENT;
        StateListDrawable surface = new StateListDrawable(); surface.addState(new int[]{-android.R.attr.state_enabled}, background(kind == 0 ? LINE : fill, 14)); surface.addState(new int[]{}, background(fill, 14));
        view.setBackgroundTintList(null); view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20bb492b), surface, background(Color.WHITE, 14)));
        view.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{MUTED, kind == 0 ? Color.WHITE : kind == 1 ? ACCENT : INK}));
        return view;
    }

    private void styleCheck(CheckBox view) { view.setMinHeight(dp(48)); view.setTextSize(14); view.setTextColor(INK); view.setButtonTintList(ColorStateList.valueOf(ACCENT)); }

    private void styleInput(EditText view, boolean station) {
        view.setSingleLine(); view.setTextColor(INK); view.setHintTextColor(MUTED); view.setTextSize(station ? 19 : 15);
        view.setMinHeight(dp(52)); view.setPadding(station ? 0 : dp(14), dp(12), station ? 0 : dp(14), dp(12));
        view.setBackground(station ? null : background(SOFT, 14));
        if (station) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private EditText field(String hint, int type) {
        add(text((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ? "비밀번호" : "아이디", 12, MUTED), 15);
        EditText view = new EditText(this); view.setHint(hint); view.setInputType(type); styleInput(view, false); add(view, 6); inputs.add(view); return view;
    }

    private EditText station(String hint, String initial) {
        add(text(hint, 11, MUTED), 0);
        AutoCompleteTextView view = new AutoCompleteTextView(this); view.setHint(hint); view.setContentDescription(hint); styleInput(view, true); view.setThreshold(1);
        view.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"서울","용산","영등포","광명","수원","천안아산","오송","대전","김천구미","서대구","동대구","경주","울산","부산","포항","경산","밀양","구포","창원중앙","마산","진주","익산","정읍","광주송정","나주","목포","전주","남원","순천","여수엑스포","청량리","상봉","만종","평창","진부","강릉","동해","안동"}));
        view.setText(initial); add(view, 0); inputs.add(view); return view;
    }

    private Spinner spinner(String[] values) {
        Spinner view = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView label = (TextView) super.getView(position, recycled, parent); label.setTextColor(INK); label.setTextSize(14); label.setSingleLine(false);
                label.setPadding(dp(14), dp(14), dp(12), dp(14)); label.setMinHeight(dp(52)); label.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down, 0); label.setCompoundDrawablePadding(dp(8)); return label;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); view.setBackground(background(SOFT, 14)); view.setPopupBackgroundDrawable(background(Color.WHITE, 14));
        view.setMinimumHeight(dp(52)); add(view, 6); inputs.add(view); return view;
    }

    private Button control(String label, Runnable action) { Button view = button(label, 1); view.setOnClickListener(v -> action.run()); add(view, 10); inputs.add(view); return view; }
    private Button settingControl(String label, Runnable action) { Button view = button(label, 1); view.setOnClickListener(v -> action.run()); add(view, 10); return view; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1); return view; }
    private GradientDrawable background(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private void add(View view, int margin) { LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.topMargin = dp(margin); content.addView(view, size); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); handler.post(refresh); }
    @Override protected void onStop() { handler.removeCallbacks(refresh); super.onStop(); }
}
