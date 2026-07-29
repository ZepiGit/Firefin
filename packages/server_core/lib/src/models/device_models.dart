class DeviceInfoDto {
  final String id;
  final String? name;
  final String? customName;
  final String? appName;
  final String? appVersion;
  final String? lastUserName;
  final String? lastUserId;
  final DateTime? dateLastActivity;
  final String? iconUrl;

  const DeviceInfoDto({
    required this.id,
    this.name,
    this.customName,
    this.appName,
    this.appVersion,
    this.lastUserName,
    this.lastUserId,
    this.dateLastActivity,
    this.iconUrl,
  });

  String get displayName => customName ?? name ?? id;

  factory DeviceInfoDto.fromJson(Map<String, dynamic> json) => DeviceInfoDto(
        id: json['Id'] as String? ?? '',
        name: json['Name'] as String?,
        customName: json['CustomName'] as String?,
        appName: json['AppName'] as String?,
        appVersion: json['AppVersion'] as String?,
        lastUserName: json['LastUserName'] as String?,
        lastUserId: json['LastUserId'] as String?,
        dateLastActivity: json['DateLastActivity'] != null
            ? DateTime.tryParse(json['DateLastActivity'] as String)
            : null,
        iconUrl: json['IconUrl'] as String?,
      );
}

class DeviceOptionsDto {
  final int? id;
  final String? deviceId;
  final String? customName;

  const DeviceOptionsDto({
    this.id,
    this.deviceId,
    this.customName,
  });

  factory DeviceOptionsDto.fromJson(Map<String, dynamic> json) =>
      DeviceOptionsDto(
        id: json['Id'] as int?,
        deviceId: json['DeviceId'] as String?,
        customName: json['CustomName'] as String?,
      );

  Map<String, dynamic> toJson() => {
        if (id != null) 'Id': id,
        if (deviceId != null) 'DeviceId': deviceId,
        'CustomName': customName,
      };
}
