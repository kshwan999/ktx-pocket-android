package kr.personal.ktxpocket;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final int INK = Color.rgb(22, 39, 47), MUTED = Color.rgb(92, 108, 116), GREEN = Color.rgb(20, 125, 100);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<View> inputs = new ArrayList<>();
    private EditText username, password, departure, arrival;
    private TextView state, detail, attempts;
    private Button dateButton, fromButton, toButton, start, stop;
    private Spinner people, seat;
    private LinearLayout content;
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(30));
        scroll.addView(content);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);

        TextView eyebrow = text("PERSONAL TRAIN ASSISTANT", 11, GREEN); eyebrow.setLetterSpacing(.14f); add(eyebrow, 0);
        TextView title = text("KTX 자리찾기", 32, INK); title.setTypeface(null, Typeface.BOLD); add(title, 10);
        add(text("내 휴대폰에서 찾고, 예약되면 알려드려요.", 14, MUTED), 8);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(background(Color.WHITE, 18));
        state = text("검색 대기", 20, GREEN); state.setTypeface(null, Typeface.BOLD); card.addView(state);
        detail = text("예약 조건을 입력하고 검색을 시작해주세요.", 14, MUTED); detail.setPadding(0, dp(10), 0, 0); card.addView(detail);
        attempts = text("", 12, MUTED); attempts.setPadding(0, dp(12), 0, 0); card.addView(attempts); add(card, 26);

        section("01", "코레일 로그인");
        username = field("회원번호 · 이메일 · 휴대폰 번호", InputType.TYPE_CLASS_TEXT); username.setSaveEnabled(false);
        password = field("코레일 비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setSaveEnabled(false);
        setupCredentialMemory();
        section("02", "어떤 열차를 찾을까요?");
        departure = station("출발역", "서울");
        arrival = station("도착역", "부산");
        dateButton = control("", () -> new DatePickerDialog(this, (picker, y, m, d) -> { date = LocalDate.of(y, m+1, d); updateTimes(); }, date.getYear(), date.getMonthValue()-1, date.getDayOfMonth()).show());
        fromButton = control("", () -> chooseTime(true));
        toButton = control("", () -> chooseTime(false));
        updateTimes();
        add(text("종료 시간은 포함하지 않습니다. 모든 시간은 한국시간 기준입니다.", 12, MUTED), 6);
        people = spinner(new String[]{"어른 1명", "어른 2명", "어른 3명", "어른 4명", "어른 5명", "어른 6명"});
        seat = spinner(new String[]{"일반실만", "일반실 우선 · 없으면 특실", "특실만", "특실 우선 · 없으면 일반실"});
        add(text("여러 명은 한 번에 예약합니다. 인접 좌석 배정은 보장되지 않습니다.", 12, MUTED), 8);
        section("03", "검색 시작");
        start = new Button(this); start.setText("예약 조건 확인하고 시작"); start.setTextColor(Color.WHITE); start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        start.setOnClickListener(v -> confirm()); add(start, 12);
        stop = new Button(this); stop.setText("검색 중지"); stop.setOnClickListener(v -> {
            if (SearchService.running) startService(new Intent(this, SearchService.class).setAction(SearchService.STOP));
        }); add(stop, 8);
        Button pay = new Button(this); pay.setText("코레일 앱에서 예약내역 · 결제 확인"); pay.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.korail.talk");
            if (launch != null) startActivity(launch); else toast("코레일톡 앱을 설치한 뒤 예약내역을 확인해주세요.");
        }); add(pay, 8);
        add(text("최소 3초 간격 · 개인용 · 집 Wi-Fi 사용 권장\n한 번에 최대 5시간 45분 검색합니다. 안드로이드나 절전 설정에 의해 더 일찍 중단될 수 있습니다. 앱을 강제 종료하면 검색도 끝납니다.", 12, MUTED), 18);
        Button battery = new Button(this); battery.setText("휴대폰 배터리 최적화 설정 열기"); battery.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))); add(battery, 10);
        TextView about = text("비공식 개인용 앱 · 원작 및 이용 조건", 12, GREEN); about.setPadding(0, dp(18), 0, dp(12)); about.setOnClickListener(v -> about()); add(about, 0);
    }

    private void confirm() {
        if (SearchService.running) return;
        if (username.getText().toString().trim().isEmpty() || password.getText().length() == 0 || departure.getText().toString().trim().isEmpty() || arrival.getText().toString().trim().isEmpty()) { toast("로그인 정보와 역 이름을 입력해주세요."); return; }
        if (departure.getText().toString().trim().equals(arrival.getText().toString().trim())) { toast("출발역과 도착역을 다르게 입력해주세요."); return; }
        ZonedDateTime end = ZonedDateTime.of(date, to, ZoneId.of("Asia/Seoul"));
        if (!to.isAfter(from) || !end.isAfter(ZonedDateTime.now(ZoneId.of("Asia/Seoul")))) { toast("출발 시간 범위를 확인해주세요."); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            toast("예약 성공 알림을 위해 알림 권한을 허용한 뒤 다시 시작해주세요."); return;
        }
        String summary = departure.getText() + " → " + arrival.getText() + "\n" + date + " · " + from + " ~ " + to + " 이전\nKTX · " + people.getSelectedItem() + " · " + seat.getSelectedItem();
        new AlertDialog.Builder(this).setTitle("이 조건으로 자동 예약할까요?")
            .setMessage(summary + "\n\n좌석이 나오면 실제 예약을 시도합니다. 결제는 직접 해야 합니다. 개인용 이용에 한하며, 조회 간격을 줄일 수 없습니다.")
            .setNegativeButton("돌아가기", null).setPositiveButton("확인하고 검색 시작", (dialog, which) -> begin()).show();
    }

    private void begin() {
        try {
            JSONObject config = new JSONObject().put("username", username.getText().toString().trim()).put("password", password.getText().toString())
                .put("departure", departure.getText().toString().trim()).put("arrival", arrival.getText().toString().trim())
                .put("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)).put("start", from.format(DateTimeFormatter.ofPattern("HHmm")))
                .put("end", to.format(DateTimeFormatter.ofPattern("HHmm"))).put("passengers", people.getSelectedItemPosition()+1)
                .put("option", options[seat.getSelectedItemPosition()]).put("confirmed", true);
            startForegroundService(new Intent(this, SearchService.class).putExtra("config", config.toString()));
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
        rememberId = new CheckBox(this); rememberId.setText("아이디 기억"); rememberId.setMinHeight(dp(48)); add(rememberId, 4); inputs.add(rememberId);
        rememberPassword = new CheckBox(this); rememberPassword.setText("비밀번호도 기억 · 이 휴대폰에 암호화 저장"); rememberPassword.setMinHeight(dp(48)); add(rememberPassword, 0); inputs.add(rememberPassword);
        credentialNote = text("선택한 정보는 검색을 시작할 때 기억하며, 다음 실행에 자동 입력됩니다.", 12, MUTED); add(credentialNote, 4);
        forgetCredentials = new Button(this); forgetCredentials.setText("저장된 로그인 정보 지우기"); add(forgetCredentials, 6);
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
        state.setText(label); state.setTextColor(kind.equals("error") || kind.equals("uncertain") ? Color.rgb(159,65,42) : GREEN);
        detail.setText(message);
        int count = prefs.getInt("attempts", 0);
        attempts.setText(count == 0 ? "휴대폰 단독 실행 · PC 연결 불필요" : count + "회 조회 · 요청 사이 최소 3초 대기");
        start.setEnabled(!active); stop.setEnabled(active);
        for (View input : inputs) input.setEnabled(!active);
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
            new AlertDialog.Builder(this).setTitle("원작 및 이용 조건").setView(scroll).setPositiveButton("닫기", null).show();
        } catch (Exception error) { toast("이용 조건 파일을 읽지 못했습니다."); }
    }
    private void chooseTime(boolean first) { LocalTime current = first ? from : to; new TimePickerDialog(this, (picker,h,m) -> { if (first) from=LocalTime.of(h,m); else to=LocalTime.of(h,m); updateTimes(); },current.getHour(),current.getMinute(),true).show(); }
    private void updateTimes() { dateButton.setText("출발 날짜   " + date); fromButton.setText("출발 시작   " + from); toButton.setText("출발 종료   " + to + " 이전"); }
    private void section(String index, String title) { TextView label = text(index + "   " + title, 17, INK); label.setTypeface(null, Typeface.BOLD); add(label, 28); }
    private EditText field(String hint, int type) { EditText view=new EditText(this); view.setSingleLine(); view.setHint(hint); view.setInputType(type); view.setTextSize(16); view.setMinHeight(dp(54)); add(view,10); inputs.add(view); return view; }
    private EditText station(String hint, String initial) { AutoCompleteTextView view=new AutoCompleteTextView(this); view.setHint(hint); view.setSingleLine(); view.setTextSize(16); view.setMinHeight(dp(54)); view.setThreshold(1); view.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,new String[]{"서울","용산","영등포","광명","수원","천안아산","오송","대전","김천구미","서대구","동대구","경주","울산","부산","포항","경산","밀양","구포","창원중앙","마산","진주","익산","정읍","광주송정","나주","목포","전주","남원","순천","여수엑스포","청량리","상봉","만종","평창","진부","강릉","동해","안동"})); view.setText(initial); add(view,10); inputs.add(view); return view; }
    private Spinner spinner(String[] values) { Spinner view=new Spinner(this); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); view.setMinimumHeight(dp(54)); add(view,10); inputs.add(view); return view; }
    private Button control(String label, Runnable action) { Button view=new Button(this); view.setText(label); view.setOnClickListener(v -> action.run()); add(view,8); inputs.add(view); return view; }
    private TextView text(String value,int size,int color) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3),1); return view; }
    private GradientDrawable background(int color,int radius) { GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private void add(View view,int margin) { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(margin); content.addView(view,params); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); handler.post(refresh); }
    @Override protected void onStop() { handler.removeCallbacks(refresh); super.onStop(); }
}
