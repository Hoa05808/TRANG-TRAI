import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/models.dart';
import 'common.dart';

/// Danh sách phân trang có "Tải thêm" và kéo để làm mới.
class PagedList<T> extends StatefulWidget {
  final Future<PageResult<T>> Function(int page) load;
  final Widget Function(T item) itemBuilder;
  final String emptyText;

  const PagedList({super.key, required this.load, required this.itemBuilder, required this.emptyText});

  @override
  State<PagedList<T>> createState() => _PagedListState<T>();
}

class _PagedListState<T> extends State<PagedList<T>> {
  final List<T> _items = [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    _items.clear();
    _page = 0;
    _hasMore = true;
    await _more();
  }

  Future<void> _more() async {
    if (_loading || !_hasMore) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final r = await widget.load(_page);
      setState(() {
        _items.addAll(r.items);
        _hasMore = r.hasMore;
        _page++;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_items.isEmpty && !_loading) {
      return RefreshIndicator(
        onRefresh: _reload,
        child: ListView(children: [EmptyState(icon: Icons.inbox_outlined, text: _error ?? widget.emptyText)]),
      );
    }
    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: _items.length + 1,
        itemBuilder: (context, i) {
          if (i < _items.length) return widget.itemBuilder(_items[i]);
          if (_loading) return const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
          if (_error != null) return Center(child: TextButton(onPressed: _more, child: Text('$_error – thử lại')));
          if (_hasMore) return Center(child: TextButton(onPressed: _more, child: const Text('Tải thêm')));
          return const SizedBox(height: 24);
        },
      ),
    );
  }
}
