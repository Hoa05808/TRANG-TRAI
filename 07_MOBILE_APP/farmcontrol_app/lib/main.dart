import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'core/farm_store.dart';
import 'core/push_service.dart';
import 'core/session_store.dart';
import 'screens/home_shell.dart';
import 'screens/login_screen.dart';
import 'theme/scada_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('vi');
  await PushService.instance.init();
  final session = SessionStore();
  await session.init();
  runApp(FarmControlApp(session: session));
}

class FarmControlApp extends StatelessWidget {
  final SessionStore session;
  const FarmControlApp({super.key, required this.session});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: session,
      child: Consumer<SessionStore>(
        builder: (context, s, _) => MaterialApp(
          title: 'FarmControl',
          debugShowCheckedModeBanner: false,
          theme: Scada.theme(Brightness.light),
          darkTheme: Scada.theme(Brightness.dark),
          themeMode: s.themeMode,
          locale: const Locale('vi'),
          supportedLocales: const [Locale('vi'), Locale('en')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: s.loggedIn ? const _LoggedIn() : const LoginScreen(),
        ),
      ),
    );
  }
}

/// Tạo FarmStore khi đăng nhập và hủy khi đăng xuất.
class _LoggedIn extends StatelessWidget {
  const _LoggedIn();

  @override
  Widget build(BuildContext context) {
    final session = context.read<SessionStore>();
    return ChangeNotifierProvider(
      key: ValueKey(session.user?.id),
      create: (_) => FarmStore(session.api)..start(),
      child: const HomeShell(),
    );
  }
}
