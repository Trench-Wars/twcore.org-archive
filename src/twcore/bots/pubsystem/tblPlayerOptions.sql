CREATE TABLE IF NOT EXISTS `tblPlayerOptions` (
  `fcName` varchar(23) NOT NULL,
  `fcTileset` varchar(20) NOT NULL DEFAULT 'bluetech',
  `ftUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`fcName`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;