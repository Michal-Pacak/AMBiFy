package pl.ambif.ambify.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.shared.AccountRules
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Tier
import pl.ambif.ambify.ui.branding.BrandLogo
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury

internal object AuthColors {
    val Gold get() = Luxury.GoldBase
    val OnGold get() = Luxury.OnGold
    val Muted get() = Luxury.TextMuted
    val Field get() = Luxury.Surface
    val FieldBorder get() = Luxury.Faint
    val Error get() = Luxury.Error
}

/** A new account without a community; founding one or joining with a code comes next ([StartScreen]). */
@Composable
fun RegisterScreen(onSignedIn: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.signIn(session.api.register(RegisterRequest(null, name, email, password, session.deviceName)))
        onSignedIn()
    }

    AuthLayout {
        AuthTopBar(onBack, pl.ambif.ambify.ui.help.Help.LOGIN)
        AuthHeader("Załóż konto", "Krąg założysz albo dołączysz do niego z kodem w następnym kroku.")
        AuthCard {
            AuthField("Twoje imię", name, { name = it }, capitalize = true)
            AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
            PasswordField("Hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
            PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
            SubmitButton("Załóż konto", submit)
        }
    }
}

/** Signed in without a community: found the first one (free) or join one with an invitation code. */
@Composable
fun StartScreen(name: String, onReady: () -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    var communityName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val found = rememberSubmit {
        session.createCommunity(communityName)
        onReady()
    }
    val join = rememberSubmit {
        session.joinCommunity(code)
        onReady()
    }

    // B-006 (canvas 0.7.9, board 5): the theme's background, two gold cards; the tapped one opens in place.
    val modules = ModuleId.entries.filter { it.tier == Tier.BASIC }.joinToString(", ") { it.label }
    AuthLayout {
        AuthTopBar(onBack = null, help = pl.ambif.ambify.ui.help.Help.START)
        AuthHeader("Witaj, $name!", "Krąg to Twoja rodzina albo paczka bliskich: wspólne listy, plan lekcji, pogoda.")
        StartChoice(
            title = "Załóż krąg",
            text = if (choice == FOUND) "Moduły na start: $modules." else "Zostaniesz jego Ownerem i zaprosisz bliskich.",
            badge = "PIERWSZY ZA DARMO".takeIf { choice == null },
            primary = true,
            folded = choice == JOIN,
            onClick = { choice = FOUND },
        ) {
            AuthField("Nazwa kręgu", communityName, { communityName = it }, capitalize = true, last = true, onDone = found.run)
            SubmitButton("Załóż krąg", found)
        }
        StartChoice(
            title = "Mam kod zaproszenia",
            text = "Kod dostajesz od Ownera lub Admina kręgu. Jest jednorazowy i ważny ${AccountRules.INVITATION_DAYS} dni.",
            badge = null,
            primary = false,
            folded = choice == FOUND,
            onClick = { choice = JOIN },
        ) {
            CodeField("Kod zaproszenia", code, { code = it })
            SubmitButton("Dołącz do kręgu", join)
        }
        TextButton(onClick = { scope.launch { session.logout() } }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
            Text("Wyloguj", color = AuthColors.Muted, fontSize = 15.sp)
        }
    }
}

private const val FOUND = "found"
private const val JOIN = "join"

/**
 * One choice of the start screen: a gold card with an icon (filled = the usual choice). Tapped, it shows its field
 * and button inside; the other one folds to a single row.
 */
@Composable
private fun StartChoice(
    title: String,
    text: String,
    badge: String?,
    primary: Boolean,
    folded: Boolean,
    onClick: () -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val opened = open && !folded
    pl.ambif.ambify.ui.theme.LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(if (folded) 14.dp else 18.dp), spacing = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !opened, onClickLabel = title) { open = true; onClick() },
            verticalAlignment = if (folded) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val side = if (folded) 36.dp else 48.dp
            Box(
                modifier = Modifier.size(side).clip(RoundedCornerShape(if (folded) 11.dp else 14.dp))
                    .then(if (primary) Modifier.background(Luxury.GoldFill) else Modifier.border(1.5.dp, Luxury.GoldBase, RoundedCornerShape(if (folded) 11.dp else 14.dp))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (primary) AppIcons.Plus else AppIcons.GroupAdd, contentDescription = null,
                    tint = if (primary) Luxury.OnGold else Luxury.GoldText, modifier = Modifier.size(side / 2),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = if (folded) 16.sp else 19.sp, fontFamily = Luxury.HeadingFont, fontWeight = FontWeight.SemiBold, color = Luxury.Text)
                if (!folded) Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = Luxury.TextMuted)
                if (badge != null && !folded) Text(
                    badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Luxury.GoldText,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Luxury.GoldBase.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (opened) form()
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, onCreateAccount: () -> Unit, onForgotPassword: () -> Unit, notice: String? = null) {
    val session = LocalAppSession.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        session.signIn(session.api.login(LoginRequest(email, password, session.deviceName)))
        onSignedIn()
    }

    // B-006 (board 6): the big gold AMBiFy, the form in a gold card, "Załóż konto" framed under it.
    AuthLayout {
        AuthTopBar(onBack = null, help = pl.ambif.ambify.ui.help.Help.LOGIN, wordmark = false)
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(28.dp))
            Text(
                "AMBiFy", fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, fontFamily = Luxury.HeadingFont,
                style = TextStyle(brush = Luxury.GoldFill),
            )
            Text("Twój krąg w jednym miejscu", color = AuthColors.Muted, fontSize = 15.sp)
        }
        notice?.let { pl.ambif.ambify.ui.components.MessageBar(pl.ambif.ambify.ui.components.MessageKind.SUCCESS, it) }
        AuthCard {
            AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
            PasswordField("Hasło", password, { password = it }, last = true, onDone = submit.run)
            SubmitButton("Zaloguj", submit)
            TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Nie pamiętam hasła", color = Luxury.GoldText, fontSize = 15.sp)
            }
        }
        if (BuildConfig.TEST_LOGIN) TestLogin(onSignedIn)
        Text("Nie masz konta?", color = AuthColors.Muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = Luxury.ButtonShape,
            border = BorderStroke(1.5.dp, Luxury.GoldBase),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Luxury.Text),
        ) {
            Text("Załóż konto", fontSize = 16.sp, fontFamily = Luxury.HeadingFont)
        }
        Text(
            "Konto zakładasz bez kodu. Krąg wybierasz w następnym kroku.",
            color = AuthColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun JoinScreen(onSignedIn: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.signIn(session.api.register(RegisterRequest(code, name, email, password, session.deviceName)))
        onSignedIn()
    }

    AuthLayout {
        AuthTopBar(onBack, pl.ambif.ambify.ui.help.Help.START)
        AuthHeader("Dołącz do kręgu", "Kod dostaniesz od Ownera lub Admina. Jest jednorazowy i ważny ${AccountRules.INVITATION_DAYS} dni.")
        AuthCard {
            CodeField("Kod zaproszenia", code, { code = it })
            AuthField("Twoje imię", name, { name = it }, capitalize = true)
            AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
            PasswordField("Hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
            PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
            SubmitButton("Dołącz do kręgu", submit)
        }
    }
}

/** "I forgot my password": the code comes from an Owner or Admin (Konto → Społeczność → ⋮ → Resetuj hasło), an Owner's from the Platform Admin. */
@Composable
fun ResetPasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.api.resetPassword(ResetPasswordRequest(email, code, password))
        onDone()
    }

    AuthLayout {
        AuthTopBar(onBack, pl.ambif.ambify.ui.help.Help.LOGIN)
        AuthHeader(
            "Nowe hasło",
            "Kod resetu dostaniesz od Ownera lub Admina swojego kręgu (Konto → Krąg → ⋮ → Resetuj hasło). " +
                "Owner dostaje kod od zespołu AMBiFy. Kod jest ważny 24 godziny.",
        )
        AuthCard {
            AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
            CodeField("Kod resetu", code, { code = it })
            PasswordField("Nowe hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
            PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
            SubmitButton("Ustaw nowe hasło", submit)
        }
    }
}

/** AMBiFy TEST only: one tap signs in with a demo account of the chosen role (test server data). */
@Composable
private fun TestLogin(onSignedIn: () -> Unit) {
    val session = LocalAppSession.current
    var chosen by remember { mutableStateOf<String?>(null) }
    val submit = rememberSubmit {
        session.signIn(session.api.login(LoginRequest(chosen!!, "test1234", session.deviceName)))
        onSignedIn()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Szybkie logowanie (test)", color = Luxury.GoldText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            "Owner" to "owner@test.ambify",
            "Admin" to "admin@test.ambify",
            "User" to "user@test.ambify",
            "Platform Admin" to "platform@test.ambify",
        ).forEach { (role, email) ->
            OutlinedButton(
                onClick = {
                    chosen = email
                    submit.run()
                },
                enabled = !submit.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = Luxury.SmallButtonShape,
                border = BorderStroke(1.dp, Luxury.GoldLink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Luxury.GoldText),
            ) { Text("Wejdź jako $role", fontSize = 16.sp) }
        }
        submit.error?.let { Text(it, color = AuthColors.Error, fontSize = 14.sp) }
    }
}

// --- building blocks ---

internal class Submit(val busy: Boolean, val error: String?, val run: () -> Unit)

/** Runs [action] once at a time and turns a failure into a message under the button. */
@Composable
internal fun rememberSubmit(action: suspend () -> Unit): Submit {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    return Submit(busy, error) {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    action()
                } catch (e: ServerError) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }
}

internal fun checkPasswords(password: String, repeat: String) {
    if (password != repeat) throw ServerError("mismatch", "Hasła nie są takie same.")
}

/** B-006: the theme's own background (no black plate any more) — the water and wallpaper show as in the app. */
@Composable
private fun AuthLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Back arrow (or nothing), the small gold AMBiFy, and "?" on the right (board 6). */
@Composable
private fun AuthTopBar(onBack: (() -> Unit)?, help: pl.ambif.ambify.ui.help.HelpScreen?, wordmark: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Wróć", tint = Luxury.GoldText) }
        if (wordmark) Text(
            "AMBiFy", color = Luxury.GoldText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
            modifier = Modifier.padding(start = if (onBack == null) 6.dp else 0.dp),
        )
        Spacer(Modifier.weight(1f))
        help?.let { pl.ambif.ambify.ui.help.HelpButton(it) }
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Luxury.Text, fontSize = 28.sp, fontFamily = Luxury.HeadingFont, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = AuthColors.Muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** The form in a gold card, like every card of the app. */
@Composable
private fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    pl.ambif.ambify.ui.theme.LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), spacing = 12.dp, content = content)
}

@Composable
internal fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    borderColor: Color = AuthColors.FieldBorder,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Luxury.GoldText, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = textStyle,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Luxury.Text,
                unfocusedTextColor = Luxury.Text,
                focusedContainerColor = AuthColors.Field,
                unfocusedContainerColor = AuthColors.Field,
                focusedBorderColor = AuthColors.Gold,
                unfocusedBorderColor = borderColor,
                cursorColor = AuthColors.Gold,
            ),
        )
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit, last: Boolean = false, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    AuthField(
        label, value, onValueChange,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            // Not focusable: the keyboard's "Next" must go to the next field, not to this button.
            IconButton(onClick = { visible = !visible }, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(
                    if (visible) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = if (visible) "Ukryj hasło" else "Pokaż hasło",
                    tint = AuthColors.Muted,
                )
            }
        },
        last = last,
        onDone = onDone,
    )
}

@Composable
private fun CodeField(label: String, value: String, onValueChange: (String) -> Unit) {
    AuthField(
        label, value, { onValueChange(it.uppercase().filter(Char::isLetterOrDigit).take(8)) },
        textStyle = TextStyle(fontSize = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center, color = Luxury.Text),
        borderColor = AuthColors.Gold,
    )
}

@Composable
private fun SubmitButton(text: String, submit: Submit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        submit.error?.let { pl.ambif.ambify.ui.components.MessageBar(pl.ambif.ambify.ui.components.MessageKind.ERROR, it) }
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(Luxury.ButtonShape)
                .background(Luxury.GoldFill, alpha = if (submit.busy) 0.6f else 1f)
                .clickable(enabled = !submit.busy, role = androidx.compose.ui.semantics.Role.Button, onClick = submit.run),
            contentAlignment = Alignment.Center,
        ) {
            if (submit.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Luxury.OnGold, strokeWidth = 2.dp)
            } else {
                Text(Luxury.label(text), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = Luxury.HeadingFont, color = Luxury.OnGold)
            }
        }
    }
}
