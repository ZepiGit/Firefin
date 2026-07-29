import 'package:flutter/material.dart';

import '../../navigation/destinations.dart';

class CustomizationEntryDescriptor {
  final IconData icon;
  final String title;
  final String subtitle;
  final String destination;

  const CustomizationEntryDescriptor({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.destination,
  });
}

List<CustomizationEntryDescriptor> buildCustomizationEntries({
  required bool isMobile,
}) => <CustomizationEntryDescriptor>[
  CustomizationEntryDescriptor(
    icon: Icons.palette,
    title: 'Theme & Appearance',
    subtitle: isMobile
        ? 'Watched indicators, backdrops'
        : 'Focus color, watched indicators, backdrops',
    destination: Destinations.settingsAppearance,
  ),
  const CustomizationEntryDescriptor(
    icon: Icons.view_sidebar,
    title: 'Navigation',
    subtitle: 'Navbar style, toolbar buttons, appearance',
    destination: Destinations.settingsNavigation,
  ),
  const CustomizationEntryDescriptor(
    icon: Icons.home,
    title: 'Home Sections',
    subtitle: 'Reorder and toggle home rows',
    destination: Destinations.settingsHomeSections,
  ),
  const CustomizationEntryDescriptor(
    icon: Icons.featured_play_list,
    title: 'Media Bar',
    subtitle: 'Featured content, appearance',
    destination: Destinations.settingsMediaBar,
  ),
  const CustomizationEntryDescriptor(
    icon: Icons.photo_library,
    title: 'Library Display',
    subtitle: 'Poster size, image type, folder view',
    destination: Destinations.settingsLibrary,
  ),
  const CustomizationEntryDescriptor(
    icon: Icons.star,
    title: 'Ratings',
    subtitle: 'MDBList, TMDB, and rating sources',
    destination: Destinations.settingsRatings,
  ),
];
