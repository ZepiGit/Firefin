class VirtualFolderInfo {
  final String name;
  final String? collectionType;
  final String itemId;
  final List<String> locations;
  final Map<String, dynamic>? libraryOptions;

  const VirtualFolderInfo({
    required this.name,
    this.collectionType,
    this.itemId = '',
    this.locations = const [],
    this.libraryOptions,
  });

  factory VirtualFolderInfo.fromJson(Map<String, dynamic> json) =>
      VirtualFolderInfo(
        name: json['Name'] as String? ?? '',
        collectionType: json['CollectionType'] as String?,
        itemId: json['ItemId'] as String? ?? '',
        locations: _stringList(json['Locations']),
        libraryOptions: json['LibraryOptions'] as Map<String, dynamic>?,
      );

  static List<String> _stringList(dynamic value) {
    if (value is List) return value.cast<String>();
    return const [];
  }
}

class ActivityLogEntry {
  final String id;
  final String name;
  final String? overview;
  final String? shortOverview;
  final String type;
  final DateTime date;
  final String? userId;
  final String severity;

  const ActivityLogEntry({
    required this.id,
    required this.name,
    this.overview,
    this.shortOverview,
    required this.type,
    required this.date,
    this.userId,
    this.severity = 'Information',
  });

  factory ActivityLogEntry.fromJson(Map<String, dynamic> json) =>
      ActivityLogEntry(
        id: (json['Id'] ?? '').toString(),
        name: json['Name'] as String? ?? '',
        overview: json['Overview'] as String?,
        shortOverview: json['ShortOverview'] as String?,
        type: json['Type'] as String? ?? '',
        date: DateTime.parse(json['Date'] as String),
        userId: json['UserId'] as String?,
        severity: json['Severity'] as String? ?? 'Information',
      );
}

class ActivityLogResult {
  final List<ActivityLogEntry> items;
  final int totalRecordCount;

  const ActivityLogResult({
    this.items = const [],
    this.totalRecordCount = 0,
  });

  factory ActivityLogResult.fromJson(Map<String, dynamic> json) =>
      ActivityLogResult(
        items: (json['Items'] as List<dynamic>?)
                ?.map(
                    (e) => ActivityLogEntry.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
        totalRecordCount: json['TotalRecordCount'] as int? ?? 0,
      );
}

class LogFileInfo {
  final String name;
  final int size;
  final DateTime dateCreated;
  final DateTime dateModified;

  const LogFileInfo({
    required this.name,
    this.size = 0,
    required this.dateCreated,
    required this.dateModified,
  });

  factory LogFileInfo.fromJson(Map<String, dynamic> json) => LogFileInfo(
        name: json['Name'] as String? ?? '',
        size: json['Size'] as int? ?? 0,
        dateCreated: DateTime.parse(json['DateCreated'] as String),
        dateModified: DateTime.parse(json['DateModified'] as String),
      );
}

class StorageInfo {
  final FolderStorageInfo? programDataFolder;
  final FolderStorageInfo? imageCacheFolder;
  final FolderStorageInfo? cacheFolder;
  final FolderStorageInfo? logFolder;
  final FolderStorageInfo? internalMetadataFolder;
  final FolderStorageInfo? transcodingTempFolder;
  final FolderStorageInfo? webFolder;
  final String programDataPath;
  final String itemsByNamePath;
  final String cachePath;
  final String logPath;
  final String internalMetadataPath;
  final String transcodingTempPath;

  const StorageInfo({
    this.programDataFolder,
    this.imageCacheFolder,
    this.cacheFolder,
    this.logFolder,
    this.internalMetadataFolder,
    this.transcodingTempFolder,
    this.webFolder,
    this.programDataPath = '',
    this.itemsByNamePath = '',
    this.cachePath = '',
    this.logPath = '',
    this.internalMetadataPath = '',
    this.transcodingTempPath = '',
  });

  static FolderStorageInfo? _readFolder(Map<String, dynamic> json, String key) {
    final direct = json[key];
    if (direct is Map<String, dynamic>) {
      return FolderStorageInfo.fromJson(direct);
    }

    final camelKey = key.isEmpty
        ? key
        : '${key[0].toLowerCase()}${key.substring(1)}';
    final camelDirect = json[camelKey];
    if (camelDirect is Map<String, dynamic>) {
      return FolderStorageInfo.fromJson(camelDirect);
    }

    return null;
  }

  static String _readPath(Map<String, dynamic> json, String key) {
    final direct = json[key];
    if (direct is String && direct.trim().isNotEmpty) {
      return direct;
    }
    if (direct is Map<String, dynamic>) {
      final nested = direct['Path'];
      if (nested is String && nested.trim().isNotEmpty) {
        return nested;
      }
    }

    final camelKey = key.isEmpty
        ? key
        : '${key[0].toLowerCase()}${key.substring(1)}';
    final camelDirect = json[camelKey];
    if (camelDirect is String && camelDirect.trim().isNotEmpty) {
      return camelDirect;
    }
    if (camelDirect is Map<String, dynamic>) {
      final nested = camelDirect['Path'] ?? camelDirect['path'];
      if (nested is String && nested.trim().isNotEmpty) {
        return nested;
      }
    }

    return '';
  }

  factory StorageInfo.fromJson(Map<String, dynamic> json) {
    final programDataFolder = _readFolder(json, 'ProgramDataFolder');
    final imageCacheFolder = _readFolder(json, 'ImageCacheFolder');
    final cacheFolder = _readFolder(json, 'CacheFolder');
    final logFolder = _readFolder(json, 'LogFolder');
    final internalMetadataFolder = _readFolder(json, 'InternalMetadataFolder');
    final transcodingTempFolder = _readFolder(json, 'TranscodingTempFolder');
    final webFolder = _readFolder(json, 'WebFolder');

    final programDataPath = _readPath(json, 'ProgramDataPath');
    final itemsByNamePath = _readPath(json, 'ItemsByNamePath');
    final cachePath = _readPath(json, 'CachePath');
    final logPath = _readPath(json, 'LogPath');
    final internalMetadataPath = _readPath(json, 'InternalMetadataPath');
    final transcodingTempPath = _readPath(json, 'TranscodingTempPath');

    return StorageInfo(
      programDataFolder: programDataFolder,
      imageCacheFolder: imageCacheFolder,
      cacheFolder: cacheFolder,
      logFolder: logFolder,
      internalMetadataFolder: internalMetadataFolder,
      transcodingTempFolder: transcodingTempFolder,
      webFolder: webFolder,
      programDataPath: programDataPath.isNotEmpty
        ? programDataPath
          : (programDataFolder?.path ?? _readPath(json, 'ProgramDataFolder')),
      itemsByNamePath: itemsByNamePath,
      cachePath: cachePath.isNotEmpty
        ? cachePath
          : (cacheFolder?.path ?? _readPath(json, 'CacheFolder')),
      logPath: logPath.isNotEmpty
        ? logPath
          : (logFolder?.path ?? _readPath(json, 'LogFolder')),
      internalMetadataPath: internalMetadataPath.isNotEmpty
        ? internalMetadataPath
          : (internalMetadataFolder?.path ?? _readPath(json, 'InternalMetadataFolder')),
      transcodingTempPath: transcodingTempPath.isNotEmpty
        ? transcodingTempPath
          : (transcodingTempFolder?.path ?? _readPath(json, 'TranscodingTempFolder')),
    );
  }
}

class FolderStorageInfo {
  final String path;
  final int freeSpace;
  final int usedSpace;
  final String? storageType;
  final String? deviceId;

  const FolderStorageInfo({
    this.path = '',
    this.freeSpace = 0,
    this.usedSpace = 0,
    this.storageType,
    this.deviceId,
  });

  int get totalSpace => usedSpace + freeSpace;

  double get usageFraction {
    final total = totalSpace;
    if (total <= 0) {
      return 0;
    }
    return usedSpace / total;
  }

  factory FolderStorageInfo.fromJson(Map<String, dynamic> json) =>
      FolderStorageInfo(
        path: (json['Path'] ?? json['path'] ?? '').toString(),
        freeSpace: (json['FreeSpace'] as num?)?.toInt() ??
            (json['freeSpace'] as num?)?.toInt() ??
            0,
        usedSpace: (json['UsedSpace'] as num?)?.toInt() ??
            (json['usedSpace'] as num?)?.toInt() ??
            0,
        storageType: (json['StorageType'] ?? json['storageType'])?.toString(),
        deviceId: (json['DeviceId'] ?? json['deviceId'])?.toString(),
      );
}

class TaskInfo {
  final String id;
  final String name;
  final String? description;
  final String? category;
  final String state;
  final double? currentProgressPercentage;
  final TaskResult? lastExecutionResult;
  final List<TaskTriggerInfo> triggers;

  const TaskInfo({
    required this.id,
    required this.name,
    this.description,
    this.category,
    this.state = 'Idle',
    this.currentProgressPercentage,
    this.lastExecutionResult,
    this.triggers = const [],
  });

  factory TaskInfo.fromJson(Map<String, dynamic> json) => TaskInfo(
        id: json['Id'] as String? ?? '',
        name: json['Name'] as String? ?? '',
        description: json['Description'] as String?,
        category: json['Category'] as String?,
        state: json['State'] as String? ?? 'Idle',
        currentProgressPercentage:
            (json['CurrentProgressPercentage'] as num?)?.toDouble(),
        lastExecutionResult: json['LastExecutionResult'] != null
            ? TaskResult.fromJson(
                json['LastExecutionResult'] as Map<String, dynamic>)
            : null,
        triggers: (json['Triggers'] as List<dynamic>?)
                ?.map(
                    (e) => TaskTriggerInfo.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
      );
}

class TaskResult {
  final DateTime startTime;
  final DateTime endTime;
  final String status;
  final String? errorMessage;

  const TaskResult({
    required this.startTime,
    required this.endTime,
    this.status = 'Completed',
    this.errorMessage,
  });

  factory TaskResult.fromJson(Map<String, dynamic> json) => TaskResult(
        startTime: DateTime.parse(json['StartTimeUtc'] as String),
        endTime: DateTime.parse(json['EndTimeUtc'] as String),
        status: json['Status'] as String? ?? 'Completed',
        errorMessage: json['ErrorMessage'] as String?,
      );
}

class TaskTriggerInfo {
  final String type;
  final int? timeOfDayTicks;
  final int? intervalTicks;
  final String? dayOfWeek;
  final int? maxRuntimeTicks;

  const TaskTriggerInfo({
    required this.type,
    this.timeOfDayTicks,
    this.intervalTicks,
    this.dayOfWeek,
    this.maxRuntimeTicks,
  });

  factory TaskTriggerInfo.fromJson(Map<String, dynamic> json) =>
      TaskTriggerInfo(
        type: json['Type'] as String? ?? '',
        timeOfDayTicks: json['TimeOfDayTicks'] as int?,
        intervalTicks: json['IntervalTicks'] as int?,
        dayOfWeek: json['DayOfWeek'] as String?,
        maxRuntimeTicks: json['MaxRuntimeTicks'] as int?,
      );

  Map<String, dynamic> toJson() => {
        'Type': type,
        if (timeOfDayTicks != null) 'TimeOfDayTicks': timeOfDayTicks,
        if (intervalTicks != null) 'IntervalTicks': intervalTicks,
        if (dayOfWeek != null) 'DayOfWeek': dayOfWeek,
        if (maxRuntimeTicks != null) 'MaxRuntimeTicks': maxRuntimeTicks,
      };
}
