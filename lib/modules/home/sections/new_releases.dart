import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shadcn_flutter/shadcn_flutter.dart';
import 'package:spotube/components/fallbacks/error_box.dart';
import 'package:spotube/components/horizontal_playbutton_card_view/horizontal_playbutton_card_view.dart';
import 'package:spotube/extensions/context.dart';
import 'package:spotube/models/metadata/metadata.dart';
import 'package:spotube/provider/metadata_plugin/album/releases.dart';
import 'package:spotube/provider/metadata_plugin/core/auth.dart';
import 'package:spotube/provider/metadata_plugin/utils/common.dart';
import 'package:spotube/services/metadata/errors/exceptions.dart';

class HomeNewReleasesSection extends HookConsumerWidget {
  const HomeNewReleasesSection({super.key});

  @override
  Widget build(BuildContext context, ref) {
    final authenticated = ref.watch(metadataPluginAuthenticatedProvider);

    // Home releases are already hidden when signed out. Don't request them
    // in that state, as the bundled provider needs an account username.
    if (authenticated.asData?.value != true) {
      return const SizedBox.shrink();
    }

    final newReleases = ref.watch(metadataPluginAlbumReleasesProvider);
    final newReleasesNotifier =
        ref.read(metadataPluginAlbumReleasesProvider.notifier);

    if (newReleases.isLoading ||
        newReleases.asData?.value.items.isEmpty == true) {
      return const SizedBox.shrink();
    }

    if (newReleases.error
        case MetadataPluginException(
          errorCode: MetadataPluginErrorCode.noDefaultMetadataPlugin,
          message: _,
        )) {
      return const SizedBox.shrink();
    }

    return HorizontalPlaybuttonCardView<SpotubeSimpleAlbumObject>(
      items: newReleases.asData?.value.items ?? [],
      title: Text(context.l10n.new_releases),
      isLoadingNextPage: newReleases.isLoadingNextPage,
      hasNextPage: newReleases.asData?.value.hasMore ?? false,
      onFetchMore: newReleasesNotifier.fetchMore,
      error: newReleases.hasError
          ? Center(
              child: ErrorBox(
                error: newReleases.error!,
                onRetry: () {
                  ref.invalidate(metadataPluginAlbumReleasesProvider);
                },
              ),
            )
          : null,
    );
  }
}
