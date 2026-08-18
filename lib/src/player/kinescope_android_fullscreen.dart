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

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

void applyKinescopeAndroidFullscreenUi({required bool fullscreen}) {
  if (defaultTargetPlatform != TargetPlatform.android) {
    return;
  }

  // Orientation is driven by native KinescopeContentOrientationController (0.1.4+):
  // portrait content stays portrait in fullscreen; landscape rotates to landscape.
  // Do not lock Flutter orientations here — that fights the native controller.
  if (fullscreen) {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    return;
  }

  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
}

void applyKinescopeAndroidPictureInPictureUi({required bool pictureInPicture}) {
  if (defaultTargetPlatform != TargetPlatform.android) {
    return;
  }

  if (pictureInPicture) {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    return;
  }

  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
}

Set<Factory<OneSequenceGestureRecognizer>> kinescopeAndroidViewGestureRecognizers() {
  return <Factory<OneSequenceGestureRecognizer>>{
    Factory<EagerGestureRecognizer>(() => EagerGestureRecognizer()),
  };
}

Widget kinescopeAndroidPlayerPlaceholder({required double aspectRatio}) {
  return AspectRatio(
    aspectRatio: aspectRatio,
    child: const ColoredBox(color: Color(0xFF000000)),
  );
}

/// Hides the platform view and waits for the EGL surface to release before
/// completing [Navigator.pop]. Required for hybrid-composition Android views.
///
/// When [onInterceptPop] returns `true` (e.g. exit fullscreen), the route is
/// kept and [onPreparePop] is not called.
class KinescopeAndroidPopGuard extends StatefulWidget {
  const KinescopeAndroidPopGuard({
    super.key,
    required this.child,
    required this.onPreparePop,
    this.onInterceptPop,
  });

  final Widget child;
  final Future<void> Function() onPreparePop;

  /// Return `true` to consume Back without popping the route.
  final Future<bool> Function()? onInterceptPop;

  @override
  State<KinescopeAndroidPopGuard> createState() =>
      _KinescopeAndroidPopGuardState();
}

class _KinescopeAndroidPopGuardState extends State<KinescopeAndroidPopGuard> {
  var _isPreparingPop = false;

  Future<void> _handlePop() async {
    if (_isPreparingPop) {
      return;
    }
    _isPreparingPop = true;
    try {
      final intercept = widget.onInterceptPop;
      if (intercept != null && await intercept()) {
        return;
      }
      await widget.onPreparePop();
      if (!mounted) {
        return;
      }
      // One frame is enough for native surfaces to hide before the route transition.
      await WidgetsBinding.instance.endOfFrame;
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop();
    } finally {
      _isPreparingPop = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop || _isPreparingPop) {
          return;
        }
        unawaited(_handlePop());
      },
      child: widget.child,
    );
  }
}

Widget kinescopeAndroidPlayerHost({
  required bool isFullscreen,
  required bool isPictureInPicture,
  required double aspectRatio,
  required Widget androidView,
}) {
  return AspectRatio(
    aspectRatio: aspectRatio,
    child: androidView,
  );
}
