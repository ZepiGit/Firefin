enum PluginStatus {
  active,
  restart,
  disabled,
  notSupported,
  malfunctioned,
  superseded,
  deleted;

  String get label {
    switch (this) {
      case PluginStatus.active:
        return 'Active';
      case PluginStatus.disabled:
        return 'Disabled';
      case PluginStatus.restart:
        return 'Restart Required';
      case PluginStatus.malfunctioned:
        return 'Malfunctioned';
      case PluginStatus.notSupported:
        return 'Not Supported';
      case PluginStatus.superseded:
        return 'Superseded';
      case PluginStatus.deleted:
        return 'Pending Removal';
    }
  }
}

PluginStatus parsePluginStatus(String? value) {
  switch (value) {
    case 'Active':
      return PluginStatus.active;
    case 'Restart':
      return PluginStatus.restart;
    case 'Disabled':
      return PluginStatus.disabled;
    case 'NotSupported':
      return PluginStatus.notSupported;
    case 'Malfunctioned':
      return PluginStatus.malfunctioned;
    case 'Superseded':
    case 'Superceded':
      return PluginStatus.superseded;
    case 'Deleted':
      return PluginStatus.deleted;
    default:
      return PluginStatus.active;
  }
}

class PluginInfo {
  final String name;
  final String version;
  final String? configurationFileName;
  final String description;
  final String id;
  final bool canUninstall;
  final bool hasImage;
  final PluginStatus status;

  const PluginInfo({
    required this.name,
    this.version = '',
    this.configurationFileName,
    this.description = '',
    required this.id,
    this.canUninstall = false,
    this.hasImage = false,
    this.status = PluginStatus.active,
  });

  factory PluginInfo.fromJson(Map<String, dynamic> json) => PluginInfo(
        name: json['Name'] as String? ?? '',
        version: json['Version'] is String
            ? json['Version'] as String
            : json['Version']?.toString() ?? '',
        configurationFileName: json['ConfigurationFileName'] as String?,
        description: json['Description'] as String? ?? '',
        id: json['Id'] as String? ?? '',
        canUninstall: json['CanUninstall'] as bool? ?? false,
        hasImage: json['HasImage'] as bool? ?? false,
        status: parsePluginStatus(json['Status'] as String?),
      );
}

class PackageInfo {
  final String name;
  final String description;
  final String overview;
  final String owner;
  final String category;
  final String id;
  final List<VersionInfo> versions;
  final String? imageUrl;

  const PackageInfo({
    required this.name,
    this.description = '',
    this.overview = '',
    this.owner = '',
    this.category = '',
    this.id = '',
    this.versions = const [],
    this.imageUrl,
  });

  factory PackageInfo.fromJson(Map<String, dynamic> json) => PackageInfo(
        name: json['name'] as String? ?? '',
        description: json['description'] as String? ?? '',
        overview: json['overview'] as String? ?? '',
        owner: json['owner'] as String? ?? '',
        category: json['category'] as String? ?? '',
        id: json['guid'] as String? ?? '',
        versions: (json['versions'] as List<dynamic>?)
                ?.map((e) => VersionInfo.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
        imageUrl: json['imageUrl'] as String?,
      );
}

class VersionInfo {
  final String version;
  final String? changelog;
  final String? targetAbi;
  final String? sourceUrl;
  final String? checksum;
  final String? timestamp;
  final String repositoryName;
  final String repositoryUrl;

  const VersionInfo({
    this.version = '',
    this.changelog,
    this.targetAbi,
    this.sourceUrl,
    this.checksum,
    this.timestamp,
    this.repositoryName = '',
    this.repositoryUrl = '',
  });

  factory VersionInfo.fromJson(Map<String, dynamic> json) => VersionInfo(
        version: json['version'] as String? ?? '',
        changelog: json['changelog'] as String?,
        targetAbi: json['targetAbi'] as String?,
        sourceUrl: json['sourceUrl'] as String?,
        checksum: json['checksum'] as String?,
        timestamp: json['timestamp'] as String?,
        repositoryName: json['repositoryName'] as String? ?? '',
        repositoryUrl: json['repositoryUrl'] as String? ?? '',
      );
}

class RepositoryInfo {
  final String name;
  final String url;
  final bool enabled;

  const RepositoryInfo({
    this.name = '',
    this.url = '',
    this.enabled = true,
  });

  factory RepositoryInfo.fromJson(Map<String, dynamic> json) => RepositoryInfo(
        name: json['Name'] as String? ?? '',
        url: json['Url'] as String? ?? '',
        enabled: json['Enabled'] as bool? ?? true,
      );

  Map<String, dynamic> toJson() => {
        'Name': name,
        'Url': url,
        'Enabled': enabled,
      };
}
