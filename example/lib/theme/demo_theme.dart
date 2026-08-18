// Copyright (c) 2021-present, Kinescope
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'package:flutter/material.dart';

/// Colors and styles aligned with kotlin-kinescope-player demo app.
abstract class DemoTheme {
  static const kinescopePrimary = Color(0xFF6161FC);
  static const demoBlack = Color(0xFF111111);
  static const demoWhite = Color(0xFFFFFFFF);
  static const playlistTextPrimary = Color(0xFF111111);
  static const emptyText = Color(0xA3111111);

  static ThemeData materialTheme() {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: kinescopePrimary,
        primary: kinescopePrimary,
        surface: demoWhite,
      ),
      scaffoldBackgroundColor: demoWhite,
      appBarTheme: const AppBarTheme(
        backgroundColor: demoBlack,
        foregroundColor: demoWhite,
        elevation: 4,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: kinescopePrimary,
        foregroundColor: demoWhite,
      ),
    );
  }

  static ButtonStyle accentButtonStyle() {
    return ElevatedButton.styleFrom(
      backgroundColor: kinescopePrimary,
      foregroundColor: demoWhite,
      minimumSize: const Size(double.infinity, 48),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      textStyle: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w500,
      ),
    );
  }
}
