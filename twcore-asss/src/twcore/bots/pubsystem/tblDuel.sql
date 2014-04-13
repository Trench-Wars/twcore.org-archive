CREATE TABLE IF NOT EXISTS `tblduel` (
  `fnDuelID` int(11) NOT NULL AUTO_INCREMENT,
  `fcNameChallenger` varchar(23) NOT NULL,
  `fcNameAccepter` varchar(23) NOT NULL,
  `fcWinner` varchar(23) NOT NULL,
  `fnScoreChallenger` int(11) NOT NULL,
  `fnScoreAccepter` int(11) NOT NULL,
  `fnShip` int(11) NOT NULL COMMENT '0=any ship',
  `fnWinByLagout` int(1) NOT NULL DEFAULT '0',
  `fnDuration` int(11) NOT NULL COMMENT 'in seconds',
  `fnMoney` int(11) NOT NULL,
  `fdDate` datetime NOT NULL,
  PRIMARY KEY (`fnDuelID`),
  KEY `fcNameChallenger` (`fcNameChallenger`),
  KEY `fcNameAccepter` (`fcNameAccepter`),
  KEY `fcWinner` (`fcWinner`),
  KEY `fnShip` (`fnShip`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;