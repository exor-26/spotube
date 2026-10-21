import 'package:dio/dio.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shadcn_flutter/shadcn_flutter.dart';
import 'package:spotube/components/horizontal_playbutton_card_view/horizontal_playbutton_card_view.dart';
import 'package:spotube/extensions/context.dart';
import 'package:spotube/models/metadata/metadata.dart';
import 'package:spotube/services/dio/dio.dart';

/// Public ListenBrainz discovery for guests using the bundled metadata plugin.
///
/// Only the two documented public endpoints are called. Personalized sections,
/// user library and plugin authentication remain unchanged.
final guestFreshAlbumsProvider = FutureProvider<List<SpotubeSimpleAlbumObject>>(
  (ref) => fetchGuestFreshAlbums(),
);

final guestPublicPlaylistsProvider =
    FutureProvider<List<SpotubeSimplePlaylistObject>>(
  (ref) => fetchGuestPublicPlaylists(),
);

Future<List<SpotubeSimpleAlbumObject>> fetchGuestFreshAlbums() async {
  final response = await globalDio.getUri(
    Uri.https('api.listenbrainz.org', '/1/explore/fresh-releases/', {
      'days': '30',
      'future': 'false',
    }),
    options: Options(receiveTimeout: const Duration(seconds: 15)),
  );

  final body = response.data;
  if (body is! Map) return [];
  final payload = body['payload'];
  if (payload is! Map || payload['releases'] is! List) return [];

  final albums = <SpotubeSimpleAlbumObject>[];
  final ids = <String>{};
  for (final item in payload['releases'] as List) {
    if (item is! Map) continue;
    final id = item['release_group_mbid']?.toString() ?? '';
    final title = item['release_name']?.toString() ?? '';
    if (id.isEmpty || title.isEmpty || !ids.add(id)) continue;

    final artistName = item['artist_credit_name']?.toString() ?? '';
    final rawArtists = item['artist_mbids'];
    final artists = <SpotubeSimpleArtistObject>[];
    if (rawArtists is List && rawArtists.isNotEmpty) {
      final artistId = rawArtists.first?.toString() ?? '';
      if (artistId.isNotEmpty) {
        artists.add(SpotubeSimpleArtistObject(
          id: artistId,
          name: artistName,
          externalUri: 'https://musicbrainz.org/artist/$artistId',
        ));
      }
    }
    final rawType =
        item['release_group_primary_type']?.toString().toLowerCase();
    final albumType = switch (rawType) {
      'single' => SpotubeAlbumType.single,
      'compilation' => SpotubeAlbumType.compilation,
      _ => SpotubeAlbumType.album,
    };

    albums.add(SpotubeSimpleAlbumObject(
      id: id,
      name: title,
      externalUri: 'https://musicbrainz.org/release-group/$id',
      artists: artists,
      images: [
        SpotubeImageObject(
          url: 'https://coverartarchive.org/release-group/$id/front-250.jpg',
          width: 250,
          height: 250,
        ),
      ],
      albumType: albumType,
      releaseDate: item['release_date']?.toString(),
    ));
    if (albums.length == 20) break;
  }
  return albums;
}

Future<List<SpotubeSimplePlaylistObject>> fetchGuestPublicPlaylists() async {
  // Public playlist search is separate from the user-specific /user routes.
  final response = await globalDio.getUri(
    Uri.https('api.listenbrainz.org', '/1/playlist/search', {
      'query': 'music',
      'count': '15',
    }),
    options: Options(receiveTimeout: const Duration(seconds: 55)),
  );
  final body = response.data;
  if (body is! Map || body['playlists'] is! List) return [];

  final playlists = <SpotubeSimplePlaylistObject>[];
  final ids = <String>{};
  for (final entry in body['playlists'] as List) {
    if (entry is! Map || entry['playlist'] is! Map) continue;
    final playlist = entry['playlist'] as Map;
    final uri = playlist['identifier']?.toString() ?? '';
    final id = Uri.tryParse(uri)?.pathSegments.lastOrNull ?? '';
    final name = playlist['title']?.toString() ?? '';
    if (id.isEmpty || name.isEmpty || !ids.add(id)) continue;
    final username = playlist['creator']?.toString() ?? 'ListenBrainz';
    final image = playlist['image'];
    playlists.add(SpotubeSimplePlaylistObject(
      id: id,
      name: name,
      description: playlist['annotation']?.toString() ?? '',
      externalUri: uri,
      owner: SpotubeUserObject(
        id: username,
        name: username,
        externalUri:
            'https://listenbrainz.org/user/${Uri.encodeComponent(username)}',
      ),
      images: image is String && image.startsWith('https://')
          ? [SpotubeImageObject(url: image)]
          : [],
    ));
    if (playlists.length == 15) break;
  }
  return playlists;
}

class GuestDiscoverySections extends ConsumerWidget {
  const GuestDiscoverySections({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final albums = ref.watch(guestFreshAlbumsProvider);
    final playlists = ref.watch(guestPublicPlaylistsProvider);
    final freshAlbums = albums.asData?.value ?? [];
    final publicPlaylists = playlists.asData?.value ?? [];
    final loading = albums.isLoading || playlists.isLoading;

    return SliverToBoxAdapter(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (freshAlbums.isNotEmpty)
            HorizontalPlaybuttonCardView<SpotubeSimpleAlbumObject>(
              items: freshAlbums,
              title: Text(context.l10n.new_releases),
              hasNextPage: false,
              isLoadingNextPage: false,
              onFetchMore: () {},
            ),
          if (publicPlaylists.isNotEmpty)
            HorizontalPlaybuttonCardView<SpotubeSimplePlaylistObject>(
              items: publicPlaylists,
              title: Text(context.l10n.playlists),
              hasNextPage: false,
              isLoadingNextPage: false,
              onFetchMore: () {},
            ),
          if (loading)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (!loading && freshAlbums.isEmpty && publicPlaylists.isEmpty)
            Padding(
              padding: const EdgeInsets.all(24),
              child: Text(context.l10n.search_to_get_results),
            ),
        ],
      ),
    );
  }
}
