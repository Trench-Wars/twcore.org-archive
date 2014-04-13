CREATE TABLE IF NOT EXISTS `tblPlayerStats` (
  `fcName` varchar(23) NOT NULL,
  `fnMoney` int(11) NOT NULL DEFAULT '0',
  `fcTileset` varchar(25) NOT NULL DEFAULT 'bluetech',
  `fnHuntWinner` int(11) NOT NULL DEFAULT '0',
  `fnKillothonWinner` int(11) NOT NULL DEFAULT '0',
  `fnBestStreak` int(11) NOT NULL DEFAULT '0',
  `fdBestStreak` datetime DEFAULT NULL,
  `ftUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`fcName`),
  KEY `fnMoney` (`fnMoney`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;