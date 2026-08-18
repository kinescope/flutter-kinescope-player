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

import 'package:flutter_kinescope_sdk_example/pages/offline_add_download_page.dart';
import 'package:flutter_kinescope_sdk_example/pages/offline_player_page.dart';
import 'package:flutter_kinescope_sdk_example/theme/demo_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_kinescope_sdk/flutter_kinescope_sdk.dart';

class OfflineDownloadPage extends StatefulWidget {
  const OfflineDownloadPage({Key? key}) : super(key: key);

  @override
  State<OfflineDownloadPage> createState() => _OfflineDownloadPageState();
}

class _OfflineDownloadPageState extends State<OfflineDownloadPage> {
  final _downloads = KinescopeOfflineDownload.instance;
  StreamSubscription<KinescopeDownloadUpdate>? _subscription;
  Timer? _progressTimer;

  List<KinescopeDownloadInfo> _items = const [];
  final Set<String> _pendingRemovalIds = {};
  bool _loading = true;
  bool _reloadInFlight = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _downloads.initialize();
    _subscription = _downloads.updates.listen((_) => _reload(silent: true));
    _startProgressPolling();
    await _reload();
  }

  void _startProgressPolling() {
    _progressTimer?.cancel();
    _progressTimer = Timer.periodic(const Duration(milliseconds: 500), (_) {
      final needsPoll = _items.any((item) => item.isDownloading) ||
          _pendingRemovalIds.isNotEmpty;
      if (!needsPoll || !mounted) {
        return;
      }
      _reload(silent: true);
    });
  }

  Future<void> _reload({bool silent = false}) async {
    if (_reloadInFlight) {
      return;
    }
    _reloadInFlight = true;
    try {
      if (!silent && mounted) {
        setState(() => _loading = true);
      }

      final items = await _downloads.getAllDownloads();
      if (!mounted) {
        return;
      }

      final visibleIds = items.map((item) => item.contentId).toSet();
      _pendingRemovalIds.removeWhere((id) => !visibleIds.contains(id));

      final visible = items
          .where(
            (item) =>
                item.isVisibleInLibrary &&
                !_pendingRemovalIds.contains(item.contentId),
          )
          .toList()
        ..sort((a, b) {
          final rank = _stateRank(a) - _stateRank(b);
          if (rank != 0) {
            return rank;
          }
          return (a.title ?? a.contentId).compareTo(b.title ?? b.contentId);
        });

      setState(() {
        _items = visible;
        _loading = false;
      });
    } finally {
      _reloadInFlight = false;
    }
  }

  int _stateRank(KinescopeDownloadInfo item) {
    if (item.isDownloading) {
      return 0;
    }
    if (item.isFailed) {
      return 1;
    }
    if (item.isCompleted) {
      return 2;
    }
    return 3;
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _openAddDownload() async {
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => const OfflineAddDownloadPage(),
      ),
    );
    await _reload(silent: true);
  }

  Future<void> _deleteDownload(KinescopeDownloadInfo item) async {
    // Optimistic remove — long downloads stay in Media3 as `removing` for a while.
    _pendingRemovalIds.add(item.contentId);
    setState(() {
      _items = _items.where((entry) => entry.contentId != item.contentId).toList();
    });

    try {
      await _downloads.removeDownload(item.contentId);
    } on Object catch (error) {
      _pendingRemovalIds.remove(item.contentId);
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to remove: $error')),
      );
      await _reload(silent: true);
      return;
    }

    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          item.isDownloading ? 'Download cancelled' : 'Video removed',
        ),
      ),
    );
    await _reload(silent: true);
  }

  void _playOffline(KinescopeDownloadInfo item) {
    if (!item.isCompleted) {
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => OfflinePlayerPage(
          contentId: item.contentId,
          title: item.title,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DemoTheme.demoWhite,
      appBar: AppBar(
        title: const Text('Offline viewing'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddDownload,
        backgroundColor: DemoTheme.kinescopePrimary,
        foregroundColor: DemoTheme.demoWhite,
        child: const Icon(Icons.add),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_items.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No downloaded videos.\nTap + to download from your project.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: DemoTheme.emptyText,
              fontSize: 16,
            ),
          ),
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: _items.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final item = _items[index];
        return _OfflineListItem(
          item: item,
          onTap: () => _playOffline(item),
          onDelete: () => _deleteDownload(item),
        );
      },
    );
  }
}

class _OfflineListItem extends StatelessWidget {
  const _OfflineListItem({
    required this.item,
    required this.onTap,
    required this.onDelete,
  });

  final KinescopeDownloadInfo item;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final percent = item.percent ?? 0;
    final hasProgress = percent > 0;

    return Material(
      color: DemoTheme.demoWhite,
      child: InkWell(
        onTap: item.isCompleted ? onTap : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.title ?? item.contentId,
                      style: const TextStyle(
                        color: DemoTheme.playlistTextPrimary,
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    if (item.isDownloading) ...[
                      const SizedBox(height: 12),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: hasProgress ? percent / 100 : null,
                          minHeight: 4,
                          color: DemoTheme.kinescopePrimary,
                          backgroundColor: const Color(0xFFE8E8E8),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        item.progressLabel,
                        style: const TextStyle(
                          fontSize: 12,
                          color: DemoTheme.emptyText,
                        ),
                      ),
                    ] else if (item.isFailed) ...[
                      const SizedBox(height: 6),
                      const Text(
                        'Download failed',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.red,
                        ),
                      ),
                    ] else if (item.qualityLabel != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        item.qualityLabel!,
                        style: const TextStyle(
                          fontSize: 12,
                          color: DemoTheme.emptyText,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              IconButton(
                onPressed: onDelete,
                icon: Icon(
                  item.isDownloading ? Icons.close : Icons.delete_outline,
                ),
                color: DemoTheme.demoBlack,
                tooltip: item.isDownloading ? 'Cancel' : 'Delete',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
