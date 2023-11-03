package mesh

import (
	"git.domain.corp/system/golib.git/log"
)

var logger = log.NewLogger("mesh")

type Mesh struct {
	stopped    int32
}

func (m *Mesh) Start() error {
	logger.Infof("Started agent")
	return nil
}

